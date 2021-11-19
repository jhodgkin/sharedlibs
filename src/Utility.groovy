
def hello(String name) {
    echo "Hello, ${name}!"
}

// CALLED LIKE SO:
// script {
//     def helpers = new conserviceBuild.helpers()
//     helpers.hello("Jeremy")
// }


// class must be Serializable so the state can be saved if the pipeline is stopped & started
class buildUtils implements Serializable {
    def env
    def steps

    buildUtils(script) {
        this.env = script.env
        this.steps = script.steps
    }

    def doThing(tasks) {
        def registeredTool = steps.tool "nameoftool3.2"
        steps.bat "echo doing thing for ${env.BUILD_TAG}"
        steps.timestamps {
            steps.bat "${registeredTool}/bin/tool ${tasks}"
        }
    }
}

// CALLED LIKE SO:
// @Library("bldtools") import conserviceBuild.helpers.buildUtils.*
// def cb = new buildUtils(this)
// 
// script {
//     cb.doThing "name of task or arg or whatever lolz"
// }

