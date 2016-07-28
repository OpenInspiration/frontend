import controllers.BreakingNews.{BreakingNewsUpdaterTest, BreakingNewsApiTest}
import controllers.HealthCheck
import org.scalatest.{BeforeAndAfterAll, Suites}
import test.{SingleServerSuite, WithMaterializer, WithTestWsClient}

class AdminJobsTestSuite extends Suites (
  new BreakingNewsApiTest,
  new BreakingNewsUpdaterTest,
  new controllers.NewsAlertControllerTest
) with SingleServerSuite
with BeforeAndAfterAll with WithMaterializer with WithTestWsClient {
  override lazy val port: Int = new HealthCheck(wsClient).testPort
}
