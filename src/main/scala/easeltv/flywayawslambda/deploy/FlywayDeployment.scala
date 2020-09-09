package easeltv.flywayawslambda.deploy

import java.nio.file.Path

import com.amazonaws.services.lambda.runtime.Context
import easeltv.flywayawslambda.MigrationRequest
import org.flywaydb.core.api.configuration.FluentConfiguration

case class FlywayDeployment(
  sourceBucket: String,
  sourcePrefix:String,
  url: String,
  user: String,
  password: String,
  location: String,
  sqlFiles: Seq[Path],
  options: Seq[FlywayOption]
)
object FlywayDeployment {
  def apply(migrationRequest: MigrationRequest, location: String, sqlFiles: Seq[Path], context: Context): FlywayDeployment = {
    FlywayDeployment(
      migrationRequest.s3BucketName,
      migrationRequest.s3Prefix,
      migrationRequest.databaseUrl,
      migrationRequest.databaseUsername,
      migrationRequest.databasePassword,
      location,
      sqlFiles,
      FlywayOption.buildOptions(migrationRequest, context)
    )
  }
}

sealed trait FlywayOption {
  def apply(flyway: FluentConfiguration): FluentConfiguration
}
object FlywayOption {
  def buildOptions(migrationRequest: MigrationRequest, context: Context): Seq[FlywayOption] = {
    val schemas = Schemas(Array(migrationRequest.schema))
    val outOfOrder = OutOfOrder(migrationRequest.outOfOrder)
    val cleanOnValidationError = CleanOnValidationError(migrationRequest.cleanOnValidationError)

    Seq(schemas, outOfOrder, cleanOnValidationError)
  }
}
case class OutOfOrder(enabled: Boolean) extends FlywayOption {
  override def apply(flyway: FluentConfiguration): FluentConfiguration = {
    flyway.outOfOrder(enabled)
    flyway
  }
}

case class CleanOnValidationError(enabled: Boolean) extends FlywayOption {
  override def apply(flyway: FluentConfiguration): FluentConfiguration = {
    flyway.cleanOnValidationError(enabled)
    flyway
  }
}

case class Schemas(schemas: Array[String]) extends FlywayOption {
  override def apply(flyway: FluentConfiguration): FluentConfiguration = {
    flyway.schemas(schemas: _*)
    flyway
  }
}
