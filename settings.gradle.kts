pluginManagement {
    repositories {
        // 国内网络可在前面加阿里云镜像：maven { url = uri("https://maven.aliyun.com/repository/google") } 等
        google(); mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "YimeiKeyboard"
include(":app")
