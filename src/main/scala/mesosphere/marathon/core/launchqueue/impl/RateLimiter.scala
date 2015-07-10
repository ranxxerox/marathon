package mesosphere.marathon.core.launchqueue.impl

import java.util.concurrent.TimeUnit

import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.state.{ AppDefinition, PathId, Timestamp }
import org.apache.log4j.Logger

import scala.concurrent.duration._

private[impl] class RateLimiter(clock: Clock) {
  import RateLimiter._

  private[this] var taskLaunchDelays = Map[(PathId, Timestamp), Delay]()

  def getDelay(app: AppDefinition): Timestamp =
    taskLaunchDelays.get(app.id -> app.version).map(_.deadline) getOrElse clock.now()

  def addDelay(app: AppDefinition): Timestamp = {
    setNewDelay(app, "Increasing delay") {
      case Some(delay) => Some(delay.increased(clock, app))
      case None        => Some(Delay(clock, app))
    }
  }

  private[this] def setNewDelay(app: AppDefinition, message: String)(
    calcDelay: Option[Delay] => Option[Delay]): Timestamp = {
    val maybeDelay: Option[Delay] = taskLaunchDelays.get(app.id -> app.version)
    calcDelay(maybeDelay) match {
      case Some(newDelay) =>
        import mesosphere.util.DurationToHumanReadable
        val now: Timestamp = clock.now()
        val priorTimeLeft = (now until maybeDelay.map(_.deadline).getOrElse(now)).toHumanReadable
        val timeLeft = (now until newDelay.deadline).toHumanReadable

        if (newDelay.deadline <= now) {
          resetDelay(app)
        }
        else {
          log.info(s"$message. Task launch delay for [${app.id}] changed from [$priorTimeLeft] to [$timeLeft].")
          taskLaunchDelays += ((app.id, app.version) -> newDelay)
        }
        newDelay.deadline

      case None =>
        resetDelay(app)
        clock.now()
    }
  }

  def resetDelay(app: AppDefinition): Unit = {
    if (taskLaunchDelays contains (app.id -> app.version))
      log.info(s"Task launch delay for [${app.id} - ${app.version}}] reset to zero")
    taskLaunchDelays = taskLaunchDelays - (app.id -> app.version)
  }
}

private object RateLimiter {
  private val log = Logger.getLogger(getClass.getName)

  private object Delay {
    def apply(clock: Clock, app: AppDefinition): Delay = Delay(clock, app.backoff)
    def apply(clock: Clock, delay: FiniteDuration): Delay = Delay(clock.now() + delay, delay)
    def none = Delay(Timestamp(0), 0.seconds)
  }

  private case class Delay(
      deadline: Timestamp,
      delay: FiniteDuration) {

    def increased(clock: Clock, app: AppDefinition): Delay = {
      val newDelay: FiniteDuration =
        app.maxLaunchDelay min FiniteDuration((delay.toNanos * app.backoffFactor).toLong, TimeUnit.NANOSECONDS)
      Delay(clock, newDelay)
    }
  }
}
