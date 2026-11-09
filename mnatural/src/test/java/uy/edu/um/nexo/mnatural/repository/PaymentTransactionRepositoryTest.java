package uy.edu.um.nexo.mnatural.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// La consulta de reservas vencidas que hay que reembolsar, sobre H2 en memoria (nunca la base real)
@DataJpaTest
class PaymentTransactionRepositoryTest {

    private static final AtomicInteger REFERENCE = new AtomicInteger();

    @Autowired
    private TestEntityManager em;
    @Autowired
    private PaymentTransactionRepository repository;

    private TestData data;
    private ActivitySchedule schedule;
    private Tourist tourist;

    @BeforeEach
    void setUp() {
        data = new TestData(em);
        var business = data.business(data.provider(ApprovalStatus.APPROVED), "Emprendimiento");
        var activity = data.activity(business, "Kayak", Department.ROCHA, "La Paloma", "1000", ActivityStatus.PUBLISHED);
        schedule = data.schedule(activity, LocalDateTime.now().plusDays(5), ScheduleStatus.SCHEDULED);
        tourist = data.tourist("Ana", "Pérez");
    }

    @Test
    void findsExpiredBookingsWithAnApprovedChargeAndNoRefundDone() {
        Booking paidExpired = booking(BookingStatus.EXPIRED);
        charge(paidExpired, PaymentStatus.APPROVED);
        Booking failedRefund = booking(BookingStatus.EXPIRED);
        refund(failedRefund, charge(failedRefund, PaymentStatus.APPROVED), PaymentStatus.ERROR);
        Booking refunded = booking(BookingStatus.EXPIRED);
        refund(refunded, charge(refunded, PaymentStatus.APPROVED), PaymentStatus.APPROVED);
        Booking unpaidExpired = booking(BookingStatus.EXPIRED);
        charge(unpaidExpired, PaymentStatus.REJECTED);
        // Una cancelada por el turista con 0 % de reembolso tiene su cobro y no se devuelve
        Booking cancelled = booking(BookingStatus.CANCELLED);
        charge(cancelled, PaymentStatus.APPROVED);
        em.flush();

        assertThat(repository.findExpiredBookingIdsWithUnrefundedCharge())
                .containsExactlyInAnyOrder(paidExpired.getId(), failedRefund.getId());
    }

    private Booking booking(BookingStatus status) {
        return data.booking(tourist, schedule, status, 1);
    }

    private PaymentTransaction charge(Booking booking, PaymentStatus status) {
        return transaction(booking, PaymentType.CHARGE, status, null);
    }

    private void refund(Booking booking, PaymentTransaction charge, PaymentStatus status) {
        transaction(booking, PaymentType.REFUND, status, charge);
    }

    private PaymentTransaction transaction(Booking booking, PaymentType type, PaymentStatus status,
                                           PaymentTransaction original) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBooking(booking);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setAttemptNumber(1);
        transaction.setExternalReference("REF-" + REFERENCE.incrementAndGet());
        transaction.setOriginalPayment(original);
        return em.persist(transaction);
    }
}
