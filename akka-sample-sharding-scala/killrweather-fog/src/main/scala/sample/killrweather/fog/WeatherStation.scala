package sample.killrweather.fog

import scala.util.Random

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

/**
  *  How many weather stations there are? Currently:
  *    "well over 10,000 manned and automatic surface weather stations,
  *    1,000 upper-air stations, 7,000 ships, 100 moored and 1,000 drifting buoys,
  *    hundreds of weather radars and 3,000 specially equipped commercial aircraft
  *    measure key parameters of the atmosphere, land and ocean surface every day.
  *    Add to these some 16 meteorological and 50 research satellites to get an idea
  *    of the size of the global network for meteorological, hydrological and other
  *    geophysical observations."
  *  - https://public.wmo.int/en/our-mandate/what-we-do/observations
  */
private[fog] object WeatherStation {

  /**
   * The World Meteorological Organization (WMO) assigns a 5-digit alpha-numeric
   * station identifier to all weather observation stations, including moored buoys,
   * drifting buoys, and C-Man. These IDs are location specific except for drifting buoys which
   * retain their identifier assigned by deployment location.
   *
   * Default parameters are only added here to keep this simple, but still
   * show actual fields.
   *
   * @param id Composite of Air Force Datsav3 station number and NCDC WBAN number
   * @param name Name of reporting station
   * @param countryCode 2 letter ISO Country ID
   * @param callSign International station call sign
   * @param lat Latitude in decimal degrees
   * @param long Longitude in decimal degrees
   * @param elevation Elevation in meters
   */
  final case class WmoId(id: String,
                         name: String = "",
                         countryCode: String = "",
                         callSign: String = "",
                         lat: Double = 0.0,
                         long: Double = 0.0,
                         elevation: Double = 0.0)

  sealed trait Command
  case object Sample extends Command

  val random = new Random()

  /** Starts a device and it's task to initiate reading data at a scheduled rate. */
  def apply(ws: WmoId, settings: FogSettings, httpPort: Int): Behavior[Sample.type] =
    Behaviors.setup { context =>
      val api = context.spawn(WeatherApi("localhost", httpPort, ws), s"api-${ws.id}")

      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Sample, Sample, settings.sampleInterval)

        Behaviors.receiveMessage[Sample.type] { _ =>
          val value = 5 + 30 * WeatherStation.random.nextDouble
          val eventTime = System.currentTimeMillis
          context.log.info("Sending weather station {} data.", ws.id)
          api ! WeatherApi.Data(ws.id, eventTime, value)
          Behaviors.same

        }
      }
    }
}
