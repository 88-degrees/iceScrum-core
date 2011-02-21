/*
* Copyright (c) 2010 iceScrum Technologies.
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
* Vincent Barrier (vincent.barrier@icescrum.com)
*/
import org.icescrum.components.UiControllerArtefactHandler
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.scaffolding.view.ScaffoldingViewResolver
import org.codehaus.groovy.grails.commons.GrailsClassUtils

class IcescrumCoreGrailsPlugin {
    def groupId = 'org.icescrum'
    // the plugin version
    def version = "1.2"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def artefacts = [new UiControllerArtefactHandler()]

    def observe = ['controllers']

    def loadAfter = ['controllers','feeds']

    // TODO Fill in these fields
    def author = "iceScrum"
    def authorEmail = "contact@icescrum.org"
    def title = "iceScrum core plugin (include domain / services / taglib)"
    def description = '''
    iceScrum core plugin (include domain / services / taglib)
'''

    // URL to the plugin's documentation
    def documentation = "http://www.icescrum.org/plugin/icescrum-core"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
        // Manually match the UIController classes
        application.controllerClasses.each {
            if (it.hasProperty(UiControllerArtefactHandler.PROPERTY)) {
                application.addArtefact(UiControllerArtefactHandler.TYPE, it)
                def plugin = it.hasProperty(UiControllerArtefactHandler.PLUGINNAME)?it.getPropertyValue(UiControllerArtefactHandler.PLUGINNAME):null
                addUIControllerMethods(it, ctx, plugin)
            }
        }
    }

    def doWithApplicationContext = { applicationContext ->
        applicationContext.bootStrapService.start()
    }

    def onChange = { event ->
        def controller = application.getControllerClass(event.source?.name)
        if (controller?.hasProperty(UiControllerArtefactHandler.PROPERTY)) {
            ScaffoldingViewResolver.clearViewCache()
            application.addArtefact(UiControllerArtefactHandler.TYPE, controller)
            def plugin = controller.hasProperty(UiControllerArtefactHandler.PLUGINNAME)?controller.getPropertyValue(UiControllerArtefactHandler.PLUGINNAME):null
            addUIControllerMethods(controller, application.mainContext, plugin)
        }
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    private addUIControllerMethods(clazz, ApplicationContext ctx, pluginName) {
        def mc = clazz.metaClass
        def dynamicActions = [
                toolbar:{->
                    try {
                        render (plugin:pluginName, template:"window/toolbar", model:[currentView:session.currentView, id:id])
                    } catch(Exception e) {
                        render ('')
                        e.printStackTrace()
                    }
                },
                toolbarWidget:{->
                    try {
                        render (plugin:pluginName, template:"widget/toolbar", model:[id:id])
                    } catch(Exception e) {
                        render ('')
                        e.printStackTrace()
                    }
                },
                titleBarContent:{
                    try {
                        render (plugin:pluginName, template:"window/titleBarContent", model:[id:id])
                    } catch(Exception e) {
                        render ('')
                        e.printStackTrace()
                    }
                },
                titleBarContentWidget:{
                    try {
                        render (plugin:pluginName, template:"widget/titleBarContent", model:[id:id])
                    } catch(Exception e) {
                        render ('')
                        e.printStackTrace()
                    }
                }
        ]

        dynamicActions.each { actionName, actionClosure ->
            if(!clazz.getPropertyValue(actionName)) {
                mc."${GrailsClassUtils.getGetterName(actionName)}" = {->
                    actionClosure.delegate = delegate
                    actionClosure.resolveStrategy = Closure.DELEGATE_FIRST
                    actionClosure
                }
                clazz.registerMapping(actionName)
            }
        }
    }

}
