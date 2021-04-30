package ticketing

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.http.Predef._

import scala.util.Random

class ConcurrencyControlSimulation extends Simulation {

  val conf = ConfigFactory.load()
  val BASE_URL = conf.getString("BASE_URL")

  val httpProtocol = http
    .baseUrl(BASE_URL)

  val authHeaders = Map(
    "Authorization" -> "Bearer ${token}",
    "Accept" -> "*/*",
    "Content-Type" -> "application/json"
  )

  val baseHeaders = Map(
    "Accept" -> "*/*",
    "Content-Type" -> "application/json"
  )

  var token = ""
  val randStringLength = 10

  def generateRandString() : String = {
    Random.alphanumeric.take(randStringLength).mkString
  }

  def generateEmail() : String = {
    (Random.alphanumeric.take(randStringLength).mkString) + "@gmail.com"
  }

  object Auth {

    val signup = exec(http("Signup")
              .post("/api/users/signup")
              .headers(baseHeaders)
              .body(StringBody(s"""{"email" : "${generateEmail()}" , "password" : "password"}"""))
              .check(status.is(201))
              .check(jsonPath("$..email").exists.saveAs("email"))
      )

    val signin = exec(http("Signin")
        .post("/api/users/signin")
        .headers(baseHeaders)
        .body(StringBody("""{"email" : "${email}" , "password" : "password"}"""))
        .check(status.is(200))
        .check(jsonPath("$..token").exists.saveAs("token")))
      .exec(session => {
        token = session("token").as[String].trim
        session
      })
  }

  object Ticket {
    val createTicket = exec(_.set("token", token))
      .exec(http("Create ticket")
      .post("/api/tickets")
      .headers(authHeaders)
      .body(StringBody(s"""{"title" : "${generateRandString()}", "price" : "10" }"""))
      .check(status.is(201))
      .check(jsonPath("$..title").exists.saveAs("title"))
      .check(jsonPath("$..id").exists.saveAs("ticketId"))
    )

    val updateTicketVariation1 = exec(_.set("token", token))
      .exec(http("Update ticket")
        .put("/api/tickets/${ticketId}")
        .headers(authHeaders)
        .body(StringBody("""{"title" : "${title}", "price" : "15" }"""))
        .check(status.is(200))
    )

    val updateTicketVariation2 = exec(_.set("token", token))
      .exec(http("Update ticket")
        .put("/api/tickets/${ticketId}")
        .headers(authHeaders)
        .body(StringBody("""{"title" : "${title}", "price" : "20" }"""))
        .check(status.is(200))
      )
  }

  val auth = scenario("setup user").exec(Auth.signup, Auth.signin)
  val ticket = scenario("setup ticket").exec(Ticket.createTicket, Ticket.updateTicketVariation1, Ticket.updateTicketVariation2)

  setUp(
    auth.inject(
      atOnceUsers(1)
    ),
    ticket.inject(
      nothingFor(2),
      atOnceUsers(5),
      rampUsers(200) during (10),
      constantUsersPerSec(20) during(10)
    )
  ).protocols(httpProtocol)

}
