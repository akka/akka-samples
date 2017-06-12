package typesystem

/**
  * http://www.shiftforward.eu/techtalks/2013/02/scala-path-dependent-types-a-real-world-example/
  */
object AlgorithmScala {
  case class Campaign(id: String, startDate: Long, endDate: Long, priority: Int) {
    def targets(req: Request): Boolean = true
  }

  case class Request(timestamp: Long, userId: String, data: String)

  trait Algorithm {
    type CampaignContainer

    def preprocess(campaigns: Seq[Campaign]): CampaignContainer
    def run(campaigns: CampaignContainer, req: Request): Option[Campaign]
  }

  class SimpleAlgorithm extends Algorithm {
    override type CampaignContainer = Stream[Campaign]

    override def preprocess(campaigns: Seq[Campaign]) =
      campaigns.sortBy { c => (c.priority, c.startDate) }.toStream

    override def run(campaigns: Stream[Campaign], req: Request) =
      campaigns.filter { _.targets(req) }.headOption
  }
  
  class OtherAlgorithm extends Algorithm {
    override type CampaignContainer = Map[String, Campaign]

    override def preprocess(campaigns: Seq[Campaign]) = ???

    override def run(campaigns: Map[String, Campaign], req: Request) = None
  }

  class Forecast {
    def getAlgorithm(className: String): Algorithm =
      Class.forName(className).newInstance().asInstanceOf[Algorithm]
    
    def forecast(campaigns: Seq[Campaign], requests: Seq[Request], algorithm: Algorithm) {
      val preprocessedCampaigns: algorithm.CampaignContainer = algorithm.preprocess(campaigns)
      val results = requests.map { req => algorithm.run(preprocessedCampaigns, req) }
    }
  }
}

