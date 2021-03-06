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


package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.icescrum.core.utils.ServicesUtils
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable

import java.sql.Timestamp


class Release extends TimeBox implements Cloneable, Attachmentable {

    static final int STATE_WAIT = 1
    static final int STATE_INPROGRESS = 2
    static final int STATE_DONE = 3

    int state = Release.STATE_WAIT
    int firstSprintIndex = 1
    String vision = "" // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    String name = "R"
    SortedSet<Sprint> sprints
    Date inProgressDate
    Date doneDate

    Integer attachments_count = 0

    static belongsTo = [parentProject: Project]

    static hasMany = [sprints: Sprint, features: Feature]

    static mappedBy = [sprints: 'parentRelease', features: 'parentRelease']

    static transients = ['firstDate', 'closable', 'activable', 'reactivable', 'meanVelocity', 'previousRelease', 'nextRelease']

    static mapping = {
        cache true
        table 'is_release'
        vision type: 'text'
        name index: 'rel_name_index'
        attachments_count(nullable: true) // Must be nullable at creation for postgres because it doesn't set default value. The not nullable constraint is added in migration.
        sprints cascade: 'all-delete-orphan', batchSize: 5, cache: true
    }

    static constraints = {
        vision nullable: true
        inProgressDate nullable: true
        doneDate nullable: true
        name(blank: false, unique: 'parentProject', shared: 'keyMaxSize')
        startDate(validator: { newStartDate, release ->
            def errors = validateStartDate(newStartDate, release.endDate)
            if (errors) {
                return errors
            }
            if (newStartDate.before(release.parentProject.startDate)) {
                return ['before.projectStartDate']
            }
            def previousRelease = release.parentProject.releases?.find { it.orderNumber == release.orderNumber - 1 }
            if (previousRelease && newStartDate.before(previousRelease.endDate)) {
                return ['before.previous']
            }
            return true
        })
        endDate(validator: { newEndDate, release ->
            def errors = validateEndDate(release.startDate, newEndDate)
            if (errors) {
                return errors
            }
            return true
        })
        state(validator: { newState, release ->
            if (newState == STATE_DONE && release.sprints.any { it.state != Sprint.STATE_DONE }) {
                return ['sprint.not.done']
            }
            return true
        })
    }

    static namedQueries = {
        findCurrentOrNextRelease { p ->
            parentProject {
                eq 'id', p
            }
            or {
                eq 'state', Release.STATE_INPROGRESS
                eq 'state', Release.STATE_WAIT
            }
            order("orderNumber", "asc")
            maxResults(1)
        }

        findCurrentOrLastRelease { p ->
            parentProject {
                eq 'id', p
            }
            or {
                eq 'state', Release.STATE_INPROGRESS
                eq 'state', Release.STATE_DONE
            }
            order("orderNumber", "desc")
            maxResults(1)
        }

        getInProject { p, id ->
            parentProject {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Release withRelease(long projectId, long id) {
        Release release = (Release) getInProject(projectId, id).list()
        if (!release) {
            throw new ObjectNotFoundException(id, 'Release')
        }
        return release
    }

    static Timestamp findLastUpdatedSprint(Release release) {
        executeQuery(
                """SELECT max(sprint.lastUpdated)
                   FROM Release release
                   INNER JOIN release.sprints as sprint
                   WHERE release = :release""", [release: release]).first() as Timestamp
    }

    int hashCode() {
        final int prime = 32
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!parentProject) ? 0 : parentProject.hashCode())
        result = prime * result + ((!id) ? 0 : id.hashCode())
        return result
    }

    boolean equals(Object obj) {
        if (this.is(obj)) {
            return true
        }
        if (obj == null) {
            return false
        }
        if (getClass() != obj.getClass()) {
            return false
        }
        final Release other = (Release) obj
        if (name == null) {
            if (other.name != null) {
                return false
            }
        } else if (name != other.name) {
            return false
        }
        if (parentProject == null) {
            if (other.parentProject != null) {
                return false
            }
        } else if (!parentProject.equals(other.parentProject)) {
            return false
        }
        return true
    }

    Date getFirstDate() {
        if (sprints?.size() > 0) {
            return sprints.asList().last().endDate
        } else {
            return startDate
        }
    }

    boolean getClosable() {
        return state == STATE_INPROGRESS && (!sprints?.size() || sprints.asList().last().state == Sprint.STATE_DONE)
    }

    boolean getActivable() {
        return state == STATE_WAIT && (orderNumber == 1 || previousRelease && previousRelease.state == STATE_DONE || previousRelease?.closable)
    }

    def getReactivable() {
        return state == STATE_DONE && (!nextRelease || nextRelease.state == STATE_WAIT)
    }

    Release getPreviousRelease() {
        return parentProject.releases.findAll { it.orderNumber < orderNumber }?.max { it.orderNumber }
    }

    Release getNextRelease() {
        return parentProject.releases.findAll { it.orderNumber > orderNumber }?.min { it.orderNumber }
    }

    Integer getMeanVelocity() {
        def doneSprints = sprints.findAll { it.state == Sprint.STATE_DONE }
        return doneSprints ? ((Integer) doneSprints.sum { it.velocity.toBigDecimal() }).intdiv(doneSprints.size()) : 0
    }

    def xml(builder) {
        builder.release(id: this.id) {
            builder.state(this.state)
            builder.endDate(this.endDate)
            builder.todoDate(this.todoDate)
            builder.doneDate(this.doneDate)
            builder.startDate(this.startDate)
            builder.orderNumber(this.orderNumber)
            builder.lastUpdated(this.lastUpdated)
            builder.dateCreated(this.dateCreated)
            builder.inProgressDate(this.inProgressDate)
            builder.firstSprintIndex(this.firstSprintIndex)
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.goal { builder.mkp.yieldUnescaped("<![CDATA[${this.goal ?: ''}]]>") }
            builder.vision { builder.mkp.yieldUnescaped("<![CDATA[${this.vision ?: ''}]]>") }
            builder.description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            builder.sprints() {
                this.sprints.each { _sprint ->
                    _sprint.xml(builder)
                }
            }
            builder.features() {
                this.features.each { _feature ->
                    feature(uid: _feature.uid)
                }
            }
            builder.attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
            builder.cliches() {
                this.cliches.sort { a, b ->
                    a.type <=> b.type ?: a.datePrise <=> b.datePrise
                }.each { _cliche ->
                    _cliche.xml(builder)
                }
            }
            exportDomainsPlugins(builder)
        }
    }

    def beforeValidate() {
        super.beforeValidate()
        vision = ServicesUtils.cleanXml(vision)
    }
}
