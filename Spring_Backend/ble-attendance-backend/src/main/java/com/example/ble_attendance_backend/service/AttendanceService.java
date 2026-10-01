package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.AttendanceHistoryResponse;
import com.example.ble_attendance_backend.dto.AttendanceRecordResponse;
import com.example.ble_attendance_backend.dto.AttendanceSessionRequest;
import com.example.ble_attendance_backend.dto.AttendanceSessionResponse;
import com.example.ble_attendance_backend.dto.ChallengeResponse;
import com.example.ble_attendance_backend.dto.MarkAttendanceRequest;
import com.example.ble_attendance_backend.dto.UpdateAttendanceRequest;
import com.example.ble_attendance_backend.dto.VerificationResponse;
import com.example.ble_attendance_backend.entity.AttendanceChallenge;
import com.example.ble_attendance_backend.entity.AttendanceRecord;
import com.example.ble_attendance_backend.entity.AttendanceSession;
import com.example.ble_attendance_backend.entity.AttendanceStatus;
import com.example.ble_attendance_backend.entity.AttendanceVerification;
import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.exception.ConflictException;
import com.example.ble_attendance_backend.exception.FaceRejectedException;
import com.example.ble_attendance_backend.exception.ForbiddenException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.repository.AttendanceChallengeRepository;
import com.example.ble_attendance_backend.repository.AttendanceRecordRepository;
import com.example.ble_attendance_backend.repository.AttendanceVerificationRepository;
import com.example.ble_attendance_backend.repository.AttendanceSessionRepository;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import com.example.ble_attendance_backend.repository.UserRepository;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.service.FaceService.Evaluation;
import com.example.ble_attendance_backend.service.FaceService.FaceSample;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
    /** Actions the phone checks with ML Kit; the app's LivenessAction enum uses the same names. */
    static final List<String> LIVENESS_ACTIONS = List.of("BLINK", "TURN_LEFT", "TURN_RIGHT", "SMILE");
    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(2);
    // Face samples a student may submit per beacon scan before having to scan again.
    private static final int FACE_ATTEMPTS_PER_CHALLENGE = 3;

    private final SecureRandom random = new SecureRandom();
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AttendanceChallengeRepository challengeRepository;
    private final AttendanceVerificationRepository verificationRepository;
    private final FaceService faceService;
    private final int actionsPerChallenge;

    public AttendanceService(AttendanceSessionRepository sessionRepository, AttendanceRecordRepository recordRepository,
                             ClassroomRepository classroomRepository, ClassroomMembershipRepository membershipRepository,
                             UserRepository userRepository, AttendanceChallengeRepository challengeRepository,
                             AttendanceVerificationRepository verificationRepository, FaceService faceService,
                             @Value("${attendance.face.challenge-actions:0}") int actionsPerChallenge) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.classroomRepository = classroomRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.verificationRepository = verificationRepository;
        this.faceService = faceService;
        this.actionsPerChallenge = Math.clamp(actionsPerChallenge, 0, LIVENESS_ACTIONS.size());
    }

    @Transactional
    public AttendanceSessionResponse createSession(AuthenticatedUser caller, AttendanceSessionRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("startTime must be before endTime");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(request.zoneId());
        } catch (DateTimeException exception) {
            throw new BadRequestException("zoneId is not a valid time zone");
        }
        Classroom classroom = requireOwnedClassroom(caller, request.classId());
        AttendanceSession session = sessionRepository.save(new AttendanceSession(
                classroom, request.date(), request.startTime(), request.endTime(), zone, BeaconCodes.newSecret()));
        recordRepository.saveAll(membershipRepository.findByClassroomId(classroom.getId()).stream()
                .map(membership -> new AttendanceRecord(session, membership.getStudent()))
                .toList());
        return AttendanceSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getClassroomSessions(AuthenticatedUser caller, Long classId) {
        requireOwnedClassroom(caller, classId);
        return sessionRepository.findByClassroomIdOrderByDateDescStartTimeDesc(classId).stream().map(AttendanceSessionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getRecords(AuthenticatedUser caller, Long sessionId) {
        requireOwnedSession(caller, sessionId);
        Set<Long> verifiedRecordIds = new HashSet<>(verificationRepository.findRecordIdsBySessionId(sessionId));
        return recordRepository.findBySessionIdOrderByStudentId(sessionId).stream()
                .map(record -> AttendanceRecordResponse.from(record, verifiedRecordIds.contains(record.getId())))
                .toList();
    }

    @Transactional
    public AttendanceRecordResponse updateRecord(AuthenticatedUser caller, Long sessionId, Long studentId, UpdateAttendanceRequest request) {
        requireOwnedSession(caller, sessionId);
        if (request.status() != AttendanceStatus.PRESENT && request.status() != AttendanceStatus.ABSENT) {
            throw new BadRequestException("status must be PRESENT or ABSENT");
        }
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        record.setStatus(request.status());
        boolean hasVerification = verificationRepository.findByRecordId(record.getId()).isPresent();
        return AttendanceRecordResponse.from(recordRepository.save(record), hasVerification);
    }

    /**
     * Step 1 of marking attendance: the phone proves it can see the classroom beacon and receives a
     * one-time challenge (random liveness actions) that must be completed by the enrolled student's face.
     */
    @Transactional
    public ChallengeResponse issueChallenge(AuthenticatedUser caller, Long sessionId, int beaconCode) {
        AttendanceSession session = requireActiveSessionForStudent(caller, sessionId);
        if (!BeaconCodes.verify(session.getBeaconSecret(), session.getId(), beaconCode, Instant.now())) {
            throw new BadRequestException("Beacon code is invalid or expired. Scan again near your teacher's device");
        }
        if (!faceService.isEnrolled(caller.id())) {
            throw new BadRequestException("Set up face verification before marking attendance");
        }
        recordRepository.findBySessionIdAndStudentId(sessionId, caller.id())
                .filter(record -> record.getStatus() == AttendanceStatus.PRESENT)
                .ifPresent(record -> {
                    throw new ConflictException("You are already marked present for this session");
                });

        Instant now = Instant.now();
        challengeRepository.deleteExpired(now);
        List<String> actions = new ArrayList<>(LIVENESS_ACTIONS);
        Collections.shuffle(actions, random);
        actions = List.copyOf(actions.subList(0, actionsPerChallenge));
        byte[] nonceBytes = new byte[32];
        random.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        Instant expiresAt = now.plus(CHALLENGE_LIFETIME);
        challengeRepository.save(new AttendanceChallenge(nonce, sessionId, caller.id(), String.join(",", actions), expiresAt));
        return new ChallengeResponse(nonce, actions, expiresAt.toEpochMilli());
    }

    /**
     * Step 2: the phone submits the face sample captured for the challenge, signed with the account's linked
     * device key. A clear match is PRESENT; a borderline one waits for teacher review. A clear mismatch gets
     * a 422 so the app can take another sample right away, up to FACE_ATTEMPTS_PER_CHALLENGE per beacon scan.
     */
    @Transactional(noRollbackFor = {BadRequestException.class, FaceRejectedException.class})
    public AttendanceRecordResponse markPresent(AuthenticatedUser caller, Long sessionId, MarkAttendanceRequest request) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT can mark attendance");
        }
        AttendanceChallenge challenge = challengeRepository.findById(request.nonce())
                .filter(candidate -> candidate.getSessionId().equals(sessionId) && candidate.getStudentId().equals(caller.id()))
                .filter(candidate -> !candidate.isUsed() && candidate.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Verification expired. Scan for the beacon again"));

        AttendanceSession session = requireSession(sessionId);
        User student = userRepository.findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + caller.id()));
        FaceSample sample = FaceService.parseSample(request);
        faceService.requireDeviceSignature(student,
                "MARK|" + sessionId + "|" + request.nonce() + "|" + FaceService.sampleDigest(sample), request.signature());

        Evaluation evaluation = faceService.evaluate(student.getId(), sample);
        if (evaluation.decision() == FaceService.Decision.REJECT) {
            challenge.recordFailedAttempt();
            int remaining = FACE_ATTEMPTS_PER_CHALLENGE - challenge.getFailedAttempts();
            if (remaining > 0) {
                throw new FaceRejectedException(evaluation.reason() + " (" + remaining + (remaining == 1 ? " try" : " tries") + " left)");
            }
            challenge.markUsed();
            throw new BadRequestException(evaluation.reason() + ". Scan for the beacon to try again");
        }
        challenge.markUsed();

        // Students who joined after the session was created have no record yet.
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, student.getId())
                .orElseGet(() -> new AttendanceRecord(session, student));
        if (record.getId() != null && record.getStatus() == AttendanceStatus.PRESENT) {
            return AttendanceRecordResponse.from(record, true);
        }
        record.setStatus(evaluation.decision() == FaceService.Decision.ACCEPT ? AttendanceStatus.PRESENT : AttendanceStatus.PENDING_REVIEW);
        AttendanceRecord savedRecord = recordRepository.save(record);

        AttendanceVerification verification = verificationRepository.findByRecordId(savedRecord.getId())
                .orElseGet(() -> new AttendanceVerification(savedRecord));
        verification.update(evaluation.similarity(), evaluation.spoofScore(), sample.photo(), evaluation.reason());
        verificationRepository.save(verification);
        if (evaluation.decision() == FaceService.Decision.ACCEPT) {
            faceService.rememberConfidentSample(student.getId(), sample, evaluation.similarity());
        }
        return AttendanceRecordResponse.from(savedRecord, true);
    }

    /** The evidence behind a face-verified mark, for the teacher's review screen. */
    @Transactional(readOnly = true)
    public VerificationResponse getVerification(AuthenticatedUser caller, Long sessionId, Long studentId) {
        requireOwnedSession(caller, sessionId);
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        AttendanceVerification verification = verificationRepository.findByRecordId(record.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No face verification for this student"));
        byte[] reference = faceService.referencePhoto(studentId);
        Base64.Encoder encoder = Base64.getEncoder();
        return new VerificationResponse(reference == null ? null : encoder.encodeToString(reference),
                encoder.encodeToString(verification.getPhoto()), verification.getSimilarity(),
                verification.getSpoofScore(), verification.getReviewReason());
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryResponse> getMyHistory(AuthenticatedUser caller, Long classId) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT has attendance history");
        }
        if (!membershipRepository.existsByClassroomIdAndStudentId(classId, caller.id())) {
            throw new ForbiddenException("You are not a member of this classroom");
        }
        return recordRepository.findBySessionClassroomIdAndStudentIdOrderBySessionDateDescSessionStartTimeDesc(classId, caller.id())
                .stream().map(AttendanceHistoryResponse::from).toList();
    }

    /** Gives a newly joined student an ABSENT record in every session of the class that hasn't ended yet. */
    @Transactional
    public void addStudentToOpenSessions(Classroom classroom, User student) {
        Instant now = Instant.now();
        recordRepository.saveAll(sessionRepository.findByClassroomIdOrderByDateDescStartTimeDesc(classroom.getId()).stream()
                .filter(session -> session.getEndInstant().isAfter(now))
                .filter(session -> !recordRepository.existsBySessionIdAndStudentId(session.getId(), student.getId()))
                .map(session -> new AttendanceRecord(session, student))
                .toList());
    }

    private AttendanceSession requireActiveSessionForStudent(AuthenticatedUser caller, Long sessionId) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT can mark attendance");
        }
        AttendanceSession session = requireSession(sessionId);
        if (!membershipRepository.existsByClassroomIdAndStudentId(session.getClassroom().getId(), caller.id())) {
            throw new ForbiddenException("You are not a member of this classroom");
        }
        Instant now = Instant.now();
        if (now.isBefore(session.getStartInstant()) || !now.isBefore(session.getEndInstant())) {
            throw new BadRequestException("Attendance session is not currently active");
        }
        return session;
    }

    private Classroom requireOwnedClassroom(AuthenticatedUser caller, Long classId) {
        Classroom classroom = classroomRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classId));
        if (caller.role() != Role.TEACHER || !classroom.getTeacher().getId().equals(caller.id())) {
            throw new ForbiddenException("Only the classroom's teacher can do this");
        }
        return classroom;
    }

    private AttendanceSession requireOwnedSession(AuthenticatedUser caller, Long sessionId) {
        AttendanceSession session = requireSession(sessionId);
        requireOwnedClassroom(caller, session.getClassroom().getId());
        return session;
    }

    private AttendanceSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Attendance session not found: " + sessionId));
    }
}
