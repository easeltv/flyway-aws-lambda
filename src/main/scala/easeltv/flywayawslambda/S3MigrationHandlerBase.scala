package easeltv.flywayawslambda

import java.text.SimpleDateFormat
import java.util.Date

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.AmazonS3
import easeltv.flywayawslambda.deploy.{FlywayDeployment, S3SourceFlywayDeployer}
import easeltv.flywayawslambda.migration.{FlywayMigrator, MigrationInfo, MigrationResult}
import spray.json.DefaultJsonProtocol

import scala.util.Try

case class MigrationRequest(s3BucketName: String, s3Prefix: String, databaseUrl: String, databaseUsername: String, databasePassword: String, schema: String, outOfOrder: Boolean, cleanOnValidationError: Boolean)

object MigrationResultProtocol extends DefaultJsonProtocol {
  import spray.json._

  implicit val DateFormat = new RootJsonFormat[Date] {
    override def write(value: Date): JsValue = if (value == null) JsNull else JsString(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(value))
    override def read(json: JsValue): Date = ???
  }
  implicit val migrationInfoFormat = jsonFormat6(MigrationInfo.apply)
  implicit val migrationResultFormat = jsonFormat5(MigrationResult.apply)
}

trait S3MigrationHandlerBase extends FlywayMigrator {

  type ResultJson = String
  type ResultStoredPath = String

  protected def migrate(migrationRequest: MigrationRequest)(implicit context: Context, s3Client: AmazonS3): Try[ResultJson] = {
    val logger = context.getLogger

    def resultJson(result: MigrationResult): ResultJson = {
      import MigrationResultProtocol._
      import spray.json._

      result.toJson.prettyPrint
    }

    def storeResult(deployment: FlywayDeployment, result: MigrationResult): ResultStoredPath = {
      val jsonPath = s"${deployment.sourcePrefix}/migration-result.json"
      s3Client.putObject(deployment.sourceBucket, jsonPath, resultJson(result))
      jsonPath
    }

    for {
      // Deploy Flyway resources.
      d <- new S3SourceFlywayDeployer(s3Client, migrationRequest).deploy
      _ = {
        logger.log(
          s"""--- Flyway configuration ------------------------------------
             |flyway.url      = ${d.url}
             |flyway.user     = ****
             |flyway.password = ****
             |
             |SQL locations   = ${d.location}
             |SQL files       = ${d.sqlFiles.mkString(", ")}
             |-------------------------------------------------------------
              """.stripMargin)
      }

      // Migrate DB.
      r = migrate(d, context)
      _ = {
        logger.log(s"${r.message}!. ${r.appliedCount} applied.")
        r.infos.foreach { i =>
          logger.log(s"Version=${i.version}, Type=${i.`type`}, State=${i.state} InstalledAt=${i.installedAt} ExecutionTime=${i.execTime} Description=${i.description}")
        }
      }

      // Store migration result.
      storedPath = storeResult(d, r)
      _ = logger.log(s"Migration result stored to ${migrationRequest.s3BucketName}/$storedPath.")

    } yield resultJson(r)
  }

}

