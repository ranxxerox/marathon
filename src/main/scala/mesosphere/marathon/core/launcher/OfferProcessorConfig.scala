package mesosphere.marathon.core.launcher

import org.rogach.scallop.ScallopConf

trait OfferProcessorConfig extends ScallopConf {
  lazy val offerMatchingTimeout = opt[Int]("offer_matching_timeout",
    descr = "Offer matching timeout (ms). Stop trying to match tasks for this offer after this time.",
    default = Some(1000))

}
