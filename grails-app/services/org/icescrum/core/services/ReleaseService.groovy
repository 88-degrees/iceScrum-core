/*
 * Copyright (c) 2015 Kagilum SAS
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.utils.DateUtils
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class ReleaseService extends IceScrumEventPublisher {

    def storyService
    def clicheService
    def sprintService
    def springSecurityService
    def grailsApplication
    def i18nService

    @PreAuthorize('(productOwner(#project) or scrumMaster(#project)) and !archivedProject(#project)')
    void save(Release release, Project project) {
        release.parentProject = project
        release.state = Release.STATE_WAIT
        if (project.releases?.size() <= 0 || project.releases == null) {
            release.inProgressDate = new Date()
            release.state = Release.STATE_INPROGRESS
        }
        release.orderNumber = (project.releases?.size() ?: 0) + 1
        release.save(flush: true)
        project.addToReleases(release)
        project.endDate = release.endDate
        publishSynchronousEvent(IceScrumEventType.CREATE, release)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void update(Release release, Date startDate = null, Date endDate = null) {
        startDate = DateUtils.getMidnightDate(startDate ?: release.startDate)
        endDate = DateUtils.getMidnightDate(endDate ?: release.endDate)
        def nextRelease = release.nextRelease
        if (nextRelease && nextRelease.startDate <= endDate) {
            def nextStartDate = endDate + 1
            if (nextStartDate >= nextRelease.endDate) {
                throw new BusinessException(code: 'is.release.error.endDate.after.next.release')
            }
            update(nextRelease, nextStartDate) // cascade the update of next releases recursively
        }
        if (!release.sprints.isEmpty()) {
            def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
            def firstSprint = release.sprints.min { it.startDate }
            if (DateUtils.getMidnightDate(firstSprint.startDate).before(startDate)) {
                if (firstSprint.state >= Sprint.STATE_INPROGRESS) {
                    throw new BusinessException(code: 'is.release.error.startDate.after.inprogress.sprint')
                }
                sprintService.update(firstSprint, startDate, (startDate + firstSprint.duration - 1), false, false)
            }
            def outOfBoundsSprints = release.sprints.findAll { DateUtils.getMidnightDate(it.startDate) >= endDate }
            if (outOfBoundsSprints) {
                Collection<Sprint> sprints = outOfBoundsSprints.findAll { Sprint sprint ->
                    return sprint.tasks || sprint.stories?.any { Story story -> story.tasks }
                }
                if (sprints) {
                    def sprintNames = sprints.collect { Sprint sprint -> i18nService.message(code: 'is.sprint') + ' ' + sprint.index }.join(', ')
                    throw new BusinessException(code: 'is.release.error.sprint.tasks', args: [sprintNames])
                }
                sprintService.delete(outOfBoundsSprints.min { it.startDate })
            }
            def overlappingSprint = release.sprints.find { DateUtils.getMidnightDate(it.endDate).after(endDate) }
            if (overlappingSprint) {
                if (overlappingSprint.state > Sprint.STATE_INPROGRESS) {
                    throw new BusinessException(code: 'is.release.error.endDate.before.inprogress.sprint')
                }
                sprintService.update(overlappingSprint, overlappingSprint.startDate, endDate, false, false)
            }
        }
        if (startDate != DateUtils.getMidnightDate(release.startDate)) {
            release.startDate = startDate
            if (release.orderNumber == 1 && release.parentProject.startDate > startDate) {
                release.parentProject.startDate = startDate
            }
        }
        if (endDate != DateUtils.getMidnightDate(release.endDate)) {
            release.endDate = endDate
            if (release.orderNumber == release.parentProject.releases.size()) {
                release.parentProject.endDate = endDate
            }
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, release)
        release.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, release, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void activate(Release release) {
        if (!release.activable) {
            throw new BusinessException(code: 'is.release.error.activate')
        }
        if (release.previousRelease?.closable) {
            close(release.previousRelease)
        }
        release.inProgressDate = new Date()
        release.state = Release.STATE_INPROGRESS
        update(release)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void reactivate(Release release) {
        if (!release.reactivable || release.parentProject.releases.find { it.state == Release.STATE_INPROGRESS }) {
            throw new BusinessException(code: 'is.release.error.reactivate')
        }
        release.state = Release.STATE_INPROGRESS
        release.doneDate = null
        update(release)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void close(Release release) {
        if (!release.closable) {
            throw new BusinessException(code: 'is.release.error.close')
        }
        release.doneDate = new Date()
        release.state = Release.STATE_DONE
        def lastSprintEndDate = release.sprints ? release.sprints.asList().last().endDate : new Date()
        update(release, null, lastSprintEndDate)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void delete(Release release) {
        if (release.state >= Release.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.release.error.not.deleted')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, release)
        if (release.sprints) {
            storyService.unPlanAll(release.sprints)
            sprintService.delete(release.sprints[0]) // Deleting the first sprint deletes all of them
        }
        release.features?.each { release.removeFromFeatures(it) }
        def project = release.parentProject
        project.removeFromReleases(release)
        if (project.releases) {
            project.releases.sort { it.startDate }.eachWithIndex { Release r, int i ->
                r.orderNumber = i + 1;
            }
            project.endDate = project.releases*.endDate.max()
        }
        project.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.DELETE, release, dirtyProperties)
    }

    @PreAuthorize('stakeHolder(#release.parentProject) or inProject(#release.parentProject)')
    def releaseBurndownValues(Release release) {
        def values = []
        def cliches = []
        // Beginning of project
        def firstClicheActivation = Cliche.findByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])
        if (firstClicheActivation) {
            cliches.add(firstClicheActivation)
        }
        // Regular close cliches
        cliches.addAll(Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"]))
        // Dynamic cliche
        if (release.state == Release.STATE_INPROGRESS) {
            Sprint sprint = release.sprints.find { it.state == Sprint.STATE_INPROGRESS }
            if (sprint) {
                cliches << [data: clicheService.generateSprintClicheData(sprint, Cliche.TYPE_CLOSE)]
            }
        }
        cliches?.eachWithIndex { cliche, index ->
            def xmlRoot = new XmlSlurper().parseText(cliche.data)
            if (xmlRoot) {
                def storyTypes = grailsApplication.config.icescrum.resourceBundles.storyTypes.keySet()
                def sprintEntry = [:]
                storyTypes.each { storyType ->
                    def value = xmlRoot."${grailsApplication.config.icescrum.resourceBundles.storyTypesCliche[storyType]}"
                    sprintEntry[storyType] = value.toString() ? value.toBigDecimal() : 0
                }
                sprintEntry.label = index == 0 ? "Start" : Sprint.getNameByReleaseAndClicheSprintId(release, xmlRoot."${Cliche.SPRINT_ID}".toString()) + "${cliche.id ? '' : " (progress)"}"
                values << sprintEntry
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#release.parentProject) or inProject(#release.parentProject)')
    def releaseVelocityValues(Release release) {
        def values = []
        Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"])?.each { cliche ->
            def xmlRoot = new XmlSlurper().parseText(cliche.data)
            if (xmlRoot) {
                values << [
                        userstories     : xmlRoot."${Cliche.FUNCTIONAL_STORY_VELOCITY}".toBigDecimal(),
                        defectstories   : xmlRoot."${Cliche.DEFECT_STORY_VELOCITY}".toBigDecimal(),
                        technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_VELOCITY}".toBigDecimal(),
                        label           : Sprint.getNameByReleaseAndClicheSprintId(release, xmlRoot."${Cliche.SPRINT_ID}".toString())
                ]
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#release.parentProject) or inProject(#release.parentProject)')
    def releaseVelocityCapacityValues(Release release) {
        def values = []
        def capacity = 0
        def label = ""
        Cliche.findAllByParentTimeBox(release, [sort: "datePrise", order: "asc"])?.each { cliche ->
            def xmlRoot = new XmlSlurper().parseText(cliche.data)
            if (xmlRoot) {
                if (cliche.type == Cliche.TYPE_ACTIVATION) {
                    capacity = xmlRoot."${Cliche.SPRINT_CAPACITY}".toBigDecimal()
                    label = Sprint.getNameByReleaseAndClicheSprintId(release, xmlRoot."${Cliche.SPRINT_ID}".toString())
                } else if (cliche.type == Cliche.TYPE_CLOSE) {
                    values << [
                            capacity: capacity,
                            velocity: xmlRoot."${Cliche.SPRINT_VELOCITY}".toBigDecimal(),
                            label   : label
                    ]
                }
            }
        }
        return values
    }

    def unMarshall(def releaseXml, def options) {
        Project project = options.project
        Release.withTransaction(readOnly: !options.save) { transaction ->
            def release = new Release(
                    state: releaseXml.state.text().toInteger(),
                    name: releaseXml.name.text(),
                    todoDate: DateUtils.parseDateFromExport(releaseXml.todoDate.text()),
                    startDate: DateUtils.parseDateFromExport(releaseXml.startDate.text()),
                    doneDate: DateUtils.parseDateFromExport(releaseXml.doneDate.text()),
                    inProgressDate: DateUtils.parseDateFromExport(releaseXml.inProgressDate.text()),
                    endDate: DateUtils.parseDateFromExport(releaseXml.endDate.text()),
                    orderNumber: releaseXml.orderNumber.text().toInteger(),
                    firstSprintIndex: releaseXml.firstSprintIndex.text().toInteger(),
                    description: releaseXml.description.text() ?: null,
                    vision: releaseXml.vision.text() ?: null,
                    goal: releaseXml.goal.text() ?: null)
            options.release = release
            if (project) {
                project.addToReleases(release)
                // Save before some hibernate stuff
                if (options.save) {
                    release.save()
                }
                def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
                releaseXml.sprints.sprint.each { sprint ->
                    sprintService.unMarshall(sprint, options)
                }
                releaseXml.features.feature.each { feature ->
                    Feature f = project.features.find { it.uid == feature.@uid.text().toInteger() }
                    if (f) {
                        release.addToFeatures(f)
                    }
                }
            }
            // Save before some hibernate stuff
            if (options.save) {
                release.save()
                if (project) {
                    releaseXml.attachments.attachment.each { _attachmentXml ->
                        def uid = options.userUIDByImportedID?."${_attachmentXml.posterId.text().toInteger()}" ?: null
                        User user = project.getUserByUidOrOwner(uid)
                        ApplicationSupport.importAttachment(release, user, options.path, _attachmentXml)
                    }
                    release.attachments_count = releaseXml.attachments.attachment.size() ?: 0
                }
            }
            // Child objects
            options.timebox = release
            releaseXml.cliches.cliche.each {
                clicheService.unMarshall(it, options)
            }
            options.timebox = null
            if (options.save) {
                release.save()
            }
            options.release = null
            return (Release) importDomainsPlugins(releaseXml, release, options)
        }
    }
}
