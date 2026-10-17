package edu.assignment.appointment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import edu.assignment.appointment.dto.BookingRequest;
import edu.assignment.appointment.model.Appointment;
import edu.assignment.appointment.repository.AppointmentRepository;
import edu.assignment.appointment.service.BookingService;
import edu.assignment.appointment.service.DownstreamClients;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

class AppointmentBookingTests {
    AppointmentRepository repository;
    DownstreamClients clients;
    BookingService service;
    @BeforeEach void setup(){ repository=mock(AppointmentRepository.class); clients=mock(DownstreamClients.class); when(clients.notifyBooking(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(DownstreamClients.NotificationOutcome.UNCONFIGURED); service=new BookingService(repository,clients); }
    void eligible(){when(clients.checkPatient(1)).thenReturn(new DownstreamClients.Eligibility(1L,true,true,true));}
    void available(){when(clients.reserve(2)).thenReturn(new DownstreamClients.Reservation("r1",2L,false));}
    void persist(){doAnswer(i->{Appointment a=i.getArgument(0);var f=Appointment.class.getDeclaredField("id");f.setAccessible(true);f.set(a,1L);return a;}).when(repository).saveAndFlush(any());}
    @Test void successfulBooking(){eligible();available();persist();when(clients.notifyBooking(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(DownstreamClients.NotificationOutcome.SENT);var r=service.book("k",new BookingRequest(1L,2L));assertThat(r.created()).isTrue();assertThat(r.appointment().getNotificationStatus()).isEqualTo("SENT");}
    @Test void ineligibleOrMissingPatient(){when(clients.checkPatient(1)).thenReturn(new DownstreamClients.Eligibility(1L,false,false,false));assertThatThrownBy(()->service.book("k",new BookingRequest(1L,2L))).isInstanceOf(BookingService.IneligiblePatientException.class);verify(clients,never()).reserve(anyLong());}
    @Test void unavailableSlotPropagates(){eligible();when(clients.reserve(2)).thenThrow(new RuntimeException("capacity exhausted"));assertThatThrownBy(()->service.book("k",new BookingRequest(1L,2L))).hasMessage("capacity exhausted");}
    @Test void sequentialReplay(){eligible();available();persist();var first=service.book("k",new BookingRequest(1L,2L)).appointment();when(repository.findByIdempotencyKey("k")).thenReturn(Optional.of(first));var replay=service.book("k",new BookingRequest(1L,2L));assertThat(replay.created()).isFalse();verify(clients,times(1)).reserve(2);}
    @Test void concurrentSameKeyCreatesOnce() throws Exception {eligible();available();persist();java.util.concurrent.atomic.AtomicReference<Appointment> stored=new java.util.concurrent.atomic.AtomicReference<>();when(repository.findByIdempotencyKey("same")).thenAnswer(i->Optional.ofNullable(stored.get()));doAnswer(i->{Appointment a=i.getArgument(0);var f=Appointment.class.getDeclaredField("id");f.setAccessible(true);f.set(a,1L);stored.set(a);return a;}).when(repository).saveAndFlush(any());ExecutorService pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->service.book("same",new BookingRequest(1L,2L)));var b=pool.submit(()->service.book("same",new BookingRequest(1L,2L)));a.get();b.get();verify(clients,times(1)).reserve(2);}finally{pool.shutdownNow();}}
    @Test void keyPayloadMismatchRejectedForPatientOrSlot(){Appointment a=new Appointment(1L,2L,"r1","k","PENDING");when(repository.findByIdempotencyKey("k")).thenReturn(Optional.of(a));assertThatThrownBy(()->service.book("k",new BookingRequest(1L,3L))).isInstanceOf(BookingService.KeyReuseException.class);assertThatThrownBy(()->service.book("k",new BookingRequest(3L,2L))).isInstanceOf(BookingService.KeyReuseException.class);}
    @Test void persistenceFailureCompensatesReservation(){eligible();available();when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("disk full"));assertThatThrownBy(()->service.book("k",new BookingRequest(1L,2L))).isInstanceOf(BookingService.PersistenceFailureException.class);verify(clients).release("r1");}
    @Test void notificationFailureDoesNotFailBooking(){eligible();available();persist();when(clients.notifyBooking(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(DownstreamClients.NotificationOutcome.FAILED);var r=service.book("k",new BookingRequest(1L,2L));assertThat(r.appointment().getNotificationStatus()).isEqualTo("FAILED");}
    @Test void databaseDuplicateRaceReturnsMatchingAppointmentAndReleasesExtraReservation(){eligible();available();Appointment winner=new Appointment(1L,2L,"winner-reservation","same-key","UNCONFIGURED");when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("[SQLITE_CONSTRAINT_UNIQUE] UNIQUE constraint failed: appointments.idempotency_key"));when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.empty(),Optional.of(winner));var result=service.book("same-key",new BookingRequest(1L,2L));assertThat(result.created()).isFalse();assertThat(result.appointment()).isSameAs(winner);verify(clients).release("r1");}
    @Test void failedCompensationIsKeptAsSuppressedCause(){eligible();available();when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("disk full"));doThrow(new RuntimeException("release unavailable")).when(clients).release("r1");var error=catchThrowable(()->service.book("k",new BookingRequest(1L,2L)));assertThat(error).isInstanceOf(BookingService.PersistenceFailureException.class);assertThat(error.getCause().getSuppressed()).hasSize(1);}
    @Test void unconfiguredNotificationIsRecordedSeparately(){eligible();available();persist();var result=service.book("k",new BookingRequest(1L,2L));assertThat(result.appointment().getNotificationStatus()).isEqualTo("UNCONFIGURED");}
    @Test void blankNotificationPathProducesUnconfiguredWithoutCallingDownstream(){var unconfigured=new DownstreamClients("http://localhost:1","http://localhost:1","http://localhost:1","");assertThat(unconfigured.notifyBooking(1,1,2,"reservation-1",Instant.parse("2026-10-17T12:00:00Z"))).isEqualTo(DownstreamClients.NotificationOutcome.UNCONFIGURED);}
    @Test void notificationStatusWriteFailureStillReturnsConfirmedBooking(){eligible();available();when(clients.notifyBooking(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(DownstreamClients.NotificationOutcome.SENT);java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();doAnswer(i->{Appointment a=i.getArgument(0);if(writes.incrementAndGet()==1){var f=Appointment.class.getDeclaredField("id");f.setAccessible(true);f.set(a,1L);return a;}throw new JpaSystemException(new RuntimeException("status write failed"));}).when(repository).saveAndFlush(any());var result=service.book("k",new BookingRequest(1L,2L));assertThat(result.created()).isTrue();assertThat(result.appointment().getNotificationStatus()).isEqualTo("PENDING");}
}
