package mesosphere.marathon.core.task.bus

import mesosphere.marathon.core.task.bus.impl.DefaultTaskBusModule

trait TaskBusModule {
  def taskStatusEmitter: TaskStatusEmitter
  def taskStatusObservables: TaskStatusObservables
}

object TaskBusModule {
  def apply(): TaskBusModule = new DefaultTaskBusModule
}
