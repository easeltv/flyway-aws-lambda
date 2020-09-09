package easeltv.flywayawslambda.migration

import com.amazonaws.services.lambda.runtime.Context
import easeltv.flywayawslambda.deploy.FlywayDeployment
import org.flywaydb.core.Flyway

import scala.util.{Failure, Success, Try}

trait FlywayMigrator {
  def migrate(deployment: FlywayDeployment, context: Context): MigrationResult = {
    val logger = context.getLogger

    val initialFlywayConfiguration = Flyway.configure
      .dataSource(deployment.url, deployment.user, deployment.password)
      .locations(deployment.location)

    val flywayConfigurationWithOptions = deployment.options.foldLeft(initialFlywayConfiguration)({
      case (configuration, option) => option.apply(configuration)
    })

    logger.log(s"Migrating schemas ${flywayConfigurationWithOptions.getSchemas.mkString(",")}")

    val flyway = flywayConfigurationWithOptions.load

    val appliedCount = Try {
      flyway.migrate
    }

    val migrationInfos = Try {
      flyway.info.all
    }

    (appliedCount, migrationInfos) match {
      case (Success(c), Success(is)) => MigrationResult.success(deployment.url, c, is.map(MigrationInfo(_)))
      case (Success(c), Failure(e)) => MigrationResult.failure(deployment.url, e, Seq())
      case (Failure(e), Success(is)) => MigrationResult.failure(deployment.url, e, is.map(MigrationInfo(_)))
      case (Failure(e1), Failure(e2)) => MigrationResult.failure(deployment.url, e1, Seq())
    }
  }

}
