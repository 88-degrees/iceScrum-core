/*
 * Copyright (c) 2017 Kagilum SAS.
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
 */
package org.icescrum.core.services

import grails.transaction.Transactional
import org.icescrum.core.domain.User
import org.icescrum.core.domain.security.UserToken
import org.icescrum.core.security.rest.TokenGenerator

@Transactional
class UserTokenService {

    def save(User user, UserToken userToken) {
        userToken.user = user
        userToken.id = TokenGenerator.generateToken()
        userToken.save(flush: true)
        userToken.refresh()
    }

    def delete(UserToken userToken) {
        def user = userToken.user
        userToken.delete()
        user.removeFromTokens(userToken)
        user.save(flush: true)
    }
}
