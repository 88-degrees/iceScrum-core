/*
 * Copyright (c) 2016 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

import grails.util.Environment
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.UserPreferences

class CoreBootStrap {

    def userService

    def init = { servletContext ->
        def dev = Environment.current == Environment.DEVELOPMENT && !System.properties['icescrum.noDummyze']
        if (dev) {
            println "Dummy Data loading...."
            if (User.count() <= 1) {
                userService.save(new User(username: "a", email: "a@gmail.com", firstName: "Roberto", password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
                userService.save(new User(username: "z", email: "z@gmail.com", firstName: "Bernardo", password: "z", preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', menu: ["feature": "1", "backlog": "2"])))
            }
        }
    }
}