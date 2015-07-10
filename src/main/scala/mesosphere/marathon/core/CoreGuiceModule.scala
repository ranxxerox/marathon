package mesosphere.marathon.core

import javax.inject.{ Named, Provider }

import akka.actor.ActorRef
import akka.event.EventStream
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Inject, Provides, Scopes, Singleton }
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.CoreGuiceModule.TaskStatusUpdateActorProvider
import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.core.launcher.OfferProcessor
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.leadership.{ LeadershipCoordinator, LeadershipModule }
import mesosphere.marathon.core.task.bus.{ TaskStatusEmitter, TaskStatusObservables }
import mesosphere.marathon.core.task.tracker.TaskStatusUpdateActor
import mesosphere.marathon.event.EventModule
import mesosphere.marathon.health.HealthCheckManager
import mesosphere.marathon.tasks.{ TaskIdUtil, TaskTracker }

/**
  * Provides the glue between guice and the core modules.
  */
class CoreGuiceModule extends AbstractModule {

  // Export classes used outside of core to guice
  @Provides @Singleton
  def leadershipModule(coreModule: CoreModule): LeadershipModule = coreModule.leadershipModule

  @Provides @Singleton
  def leadershipCoordinator(
    leadershipModule: LeadershipModule,
    @Named("taskStatusUpdate") makeSureToInitializeThisBeforeCreatingCoordinator: ActorRef): LeadershipCoordinator =
    leadershipModule.coordinator()

  @Provides @Singleton
  def clock(coreModule: CoreModule): Clock = coreModule.clock

  @Provides @Singleton
  def offerProcessor(coreModule: CoreModule): OfferProcessor = coreModule.launcherModule.offerProcessor

  @Provides @Singleton
  def taskStatusEmitter(coreModule: CoreModule): TaskStatusEmitter = coreModule.taskBusModule.taskStatusEmitter

  @Provides @Singleton
  def taskStatusObservable(coreModule: CoreModule): TaskStatusObservables =
    coreModule.taskBusModule.taskStatusObservables

  @Provides @Singleton
  final def taskQueue(coreModule: CoreModule): LaunchQueue = coreModule.appOfferMatcherModule.taskQueue

  override def configure(): Unit = {
    bind(classOf[CoreModule]).to(classOf[DefaultCoreModule]).in(Scopes.SINGLETON)
    bind(classOf[ActorRef])
      .annotatedWith(Names.named("taskStatusUpdate"))
      .toProvider(classOf[TaskStatusUpdateActorProvider])
      .asEagerSingleton()
  }
}

object CoreGuiceModule {
  class TaskStatusUpdateActorProvider @Inject() (
      leadershipModule: LeadershipModule,
      taskStatusObservable: TaskStatusObservables,
      @Named(EventModule.busName) eventBus: EventStream,
      @Named("schedulerActor") schedulerActor: ActorRef,
      taskIdUtil: TaskIdUtil,
      healthCheckManager: HealthCheckManager,
      taskTracker: TaskTracker,
      marathonSchedulerDriverHolder: MarathonSchedulerDriverHolder) extends Provider[ActorRef] {

    override def get(): ActorRef = {
      val props = TaskStatusUpdateActor.props(
        taskStatusObservable, eventBus, schedulerActor, taskIdUtil, healthCheckManager, taskTracker,
        marathonSchedulerDriverHolder
      )
      leadershipModule.startWhenLeader(props, "taskStatusUpdate", considerPreparedOnStart = false)
    }
  }
}
