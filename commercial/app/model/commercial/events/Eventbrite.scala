package model.commercial.events

import java.lang.System._

import commercial.feeds._
import common.{ExecutionContexts, Logging}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Eventbrite extends ExecutionContexts with Logging {

  case class Response(pagination: Pagination, events: Seq[Event])

  case class Pagination(page_number: Int, page_count: Int)

  case class Event(id: String,
                   name: String,
                   start_date: DateTime,
                   url: String,
                   description: String,
                   image_url: Option[String],
                   status: String,
                   venue: Venue,
                   tickets: Seq[Ticket],
                   capacity: Int)

  case class Ticket(hidden: Boolean,
                    donation: Boolean,
                    price: Double,
                    quantity_total: Int,
                    quantity_sold: Int)

  case class Venue(name: Option[String],
                   address: Option[String],
                   address2: Option[String],
                   city: Option[String],
                   country: Option[String],
                   postcode: Option[String]) {

    val description = Eventbrite.venueDescription(this)
  }

  def venueDescription(v: Venue): String = Seq(v.name, v.city orElse v.country).flatten mkString ", "

  implicit val jodaDateTimeFormats: Format[DateTime] = {

    val dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
    Format(Reads.jodaDateReads(dateFormat), Writes.jodaDateWrites(dateFormat))
  }

  implicit val ticketReads: Reads[Ticket] = (
    (JsPath \ "hidden").read[Boolean] and
    (JsPath \ "donation").read[Boolean] and
    (JsPath \ "cost" \ "value").read[Double].map(pence => pence / 100) and
    (JsPath \ "quantity_total").read[Int] and
    (JsPath \ "quantity_sold").read[Int]
    ) (Ticket.apply _)

  implicit val ticketWrites: Writes[Ticket] = Json.writes[Ticket]

  implicit val venueReads: Reads[Venue] = (
    (JsPath \ "name").readNullable[String] and
    (JsPath \ "address" \ "address_1").readNullable[String] and
    (JsPath \ "address" \ "address_2").readNullable[String] and
    (JsPath \ "address" \ "city").readNullable[String] and
    (JsPath \ "address" \ "country").readNullable[String] and
    (JsPath \ "address" \ "postal_code").readNullable[String]
    ) (Venue.apply _)

  implicit val venueWrites: Writes[Venue] = Json.writes[Venue]

  implicit val eventReads: Reads[Event] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "name" \ "text").read[String] and
    (JsPath \ "start" \ "utc").read[DateTime] and
    (JsPath \ "url").read[String] and
    (JsPath \ "description" \ "html").read[String] and
    (JsPath \ "image_url").readNullable[String] and // not present in JSON; see usages of `buildEventWithImageSrc`
    (JsPath \ "status").read[String] and
    (JsPath \ "venue").read[Venue] and
    (JsPath \ "ticket_classes").read[Seq[Ticket]].map(excludeFreeAndHidenTickets) and
    (JsPath \ "capacity").read[Int]
    ) (Event.apply _)

  implicit val eventWrites: Writes[Event] = Json.writes[Event]

  // we don't want to include donation/hidden ticket prices - these are odd cases and don't bring in the $$$
  def excludeFreeAndHidenTickets(tickets: Seq[Ticket]): Seq[Ticket] =
    tickets.filterNot(ticket => ticket.hidden || ticket.donation)


  implicit val paginationFormats: Format[Pagination] = Json.format[Pagination]

  implicit val responseFormats: Format[Response] = Json.format[Response]

  def buildEventWithImageSrc(event: Event, src: String): Event = event.copy(image_url=Some(src))

  def parsePagesOfEvents(feedMetaData: FeedMetaData, feedContent: => Option[String]): Future[ParsedFeed[Event]] = {

    feedMetaData.parseSwitch.isGuaranteedSwitchedOn flatMap { switchedOn =>
      if (switchedOn) {
        val start = currentTimeMillis
        feedContent map { body =>
          val responses = Json.parse(body).as[Seq[Response]]
          val events = responses flatMap {_.events}

          Future(ParsedFeed(
            events,
            Duration(currentTimeMillis - start, MILLISECONDS))
          )
        } getOrElse {
          Future.failed(MissingFeedException(feedMetaData.name))
        }
      } else {
        Future.failed(SwitchOffException(feedMetaData.parseSwitch.name))
      }
    } recoverWith {
      case NonFatal(e) => Future.failed(e)
    }
  }

  trait TicketHandler {
    def tickets: Seq[Ticket]

    lazy val displayPriceRange = {

      def format(price: Double): String = f"£$price%,.2f"

      val prices = tickets.map(_.price)
      val (low, high) = (prices.min, prices.max)

      if (low == high) format(high) else s"${format(low)} to ${high}"
    }

    lazy val ratioTicketsLeft = 1 - (tickets.map(_.quantity_sold).sum.toDouble / tickets.map(_.quantity_total).sum)
  }

  trait EventHandler {
    def status: String
    lazy val isOpen = { status == "live" }
  }
}
