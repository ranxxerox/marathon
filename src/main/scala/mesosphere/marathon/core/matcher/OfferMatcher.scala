package mesosphere.marathon.core.matcher

import mesosphere.marathon.core.matcher.OfferMatcher.MatchedTasks
import mesosphere.marathon.state.Timestamp
import org.apache.mesos.Protos.{ Offer, OfferID, TaskInfo }

import scala.concurrent.Future

object OfferMatcher {
  case class TaskWithSource(source: TaskLaunchSource, taskInfo: TaskInfo) {
    def accept(): Unit = source.taskLaunchAccepted(taskInfo)
    def reject(): Unit = source.taskLaunchRejected(taskInfo)
  }

  /**
    * Reply from an offer matcher to a MatchOffer. If the offer match
    * could not match the offer in any way it should simply leave the tasks
    * collection empty.
    *
    * To increase fairness between matchers, each normal matcher should only launch as
    * few tasks as possible per offer -- usually one. Multiple tasks could be used
    * if the tasks need to be colocated. The OfferMultiplexer tries to summarize suitable
    * matches from multiple offer matches into one response.
    *
    * A MatchedTasks reply does not guarantee that these tasks can actually be launched.
    * The launcher of message should setup some kind of timeout mechanism and handle
    * taskLaunchAccepted/taskLaunchRejected calls appropriatedly.
    */
  case class MatchedTasks(offerId: OfferID, tasks: Seq[TaskWithSource])

  trait TaskLaunchSource {
    def taskLaunchAccepted(taskInfo: TaskInfo)
    def taskLaunchRejected(taskInfo: TaskInfo)
  }
}

/**
  * Tries to match offers with given tasks.
  */
trait OfferMatcher {
  /**
    * Process offer and return the tasks that this matcher wants to launch.
    *
    * The offer matcher can expect either a taskLaunchAccepted or a taskLaunchRejected call
    * for every returned [[TaskInfo]].
    */
  def processOffer(deadline: Timestamp, offer: Offer): Future[MatchedTasks]
}
