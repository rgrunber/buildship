package org.eclipse.buildship.core

import java.util.concurrent.CountDownLatch
import java.util.function.Function

import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job

import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class GradleBuildConnectionCancellationTest extends ProjectSynchronizationSpecification {

    File location

    def setup() {
        location = dir('app') {
            file 'build.gradle', '''
                Thread.sleep(300)
            '''
        }
    }

    def "Can cancel action"() {
        setup:
        CountDownLatch latch = new CountDownLatch(1)
        Job job = new ModelQueryJob(latch, location)

        when:
        job.schedule()
        latch.await()
        job.cancel()
        job.join()

        then:
        job.result.severity == IStatus.CANCEL
    }

    class ModelQueryJob extends Job {

        CountDownLatch latch
        GradleBuild gradleBuild

        private ModelQueryJob(CountDownLatch latch, File location) {
            this(latch, gradleBuildFor(location))
        }

        private ModelQueryJob(CountDownLatch latch, IProject project) {
            this(latch, gradleBuildFor(project))
        }

        private ModelQueryJob(CountDownLatch latch, GradleBuild gradleBuild) {
            super("Model query job")
            this.latch = latch
            this.gradleBuild = gradleBuild
        }

        IStatus run(IProgressMonitor monitor) {
            latch.countDown()
            Function query = { ProjectConnection c -> c.model(GradleProject).get() }
            try {
                gradleBuild.withConnection(query, monitor)
                return Status.OK_STATUS
            } catch (BuildCancelledException e) {
                Status.CANCEL_STATUS
            }
        }
    }
}
