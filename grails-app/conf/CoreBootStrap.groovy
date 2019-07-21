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
        if (Environment.current == Environment.DEVELOPMENT && !System.properties['icescrum.noDummyze'] && User.count() <= 1) {
            println "Creating dummy users"
            userService.save(new User(username: "az", email: "az@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ae", email: "ae@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ad", email: "ad@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ac", email: "ac@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "av", email: "av@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ab", email: "ab@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ah", email: "ah@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ap", email: "ap@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "aa", email: "aa@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "aq", email: "aq@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "aw", email: "aw@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "ax", email: "ax@gmail.com", firstName: "Roberto", lastName: 'Doe', password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant')))
            userService.save(new User(username: "z", email: "z@gmail.com", firstName: "Bernardo", lastName: 'Doe', password: "z", preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', enableDarkMode: true, menu: ["taskBoard": "1", "planning": "2", "backlog": "3", "feature": "4", "project": "5"])))
        }
    }
}