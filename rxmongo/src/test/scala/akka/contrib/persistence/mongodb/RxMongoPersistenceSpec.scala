package akka.contrib.persistence.mongodb

import akka.pattern.CircuitBreaker
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import scala.concurrent._
import duration._

trait RxMongoPersistenceSpec extends MongoPersistenceSpec[RxMongoDriver, BSONCollection] { self: TestKit =>

  lazy val connection = {
    val conn = new MongoDriver().connection(s"$embedConnectionURL:$embedConnectionPort" :: Nil)
    Await.result(conn.waitForPrimary(3.seconds),4.seconds)
    conn
  }
  lazy val specDb = connection(embedDB)

  class SpecDriver extends RxMongoDriver(system, ConfigFactory.empty()) {
    override def db = specDb
    override lazy val breaker = CircuitBreaker(system.scheduler, 0, 10.seconds, 10.seconds)
    override def collection(name: String) = specDb(name)
  }

  val driver = new SpecDriver

  def withCollection(name: String)(testCode: BSONCollection => Any): Unit = {
    val collection = specDb[BSONCollection](name)
    try {
      testCode(collection)
      ()
    } finally {
      Await.ready(collection.drop(),3.seconds)
      ()
    }
  }

  def withEmptyJournal(testCode: BSONCollection => Any) = withCollection(driver.journalCollectionName) { coll =>
    Await.result(coll.remove(BSONDocument.empty),3.seconds)
    testCode(coll)
  }

  def withJournal(testCode: BSONCollection => Any) =
    withCollection(driver.journalCollectionName)(testCode)

  def withSnapshot(testCode: BSONCollection => Any) =
    withCollection(driver.snapsCollectionName)(testCode)

}
