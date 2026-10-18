package ru.distillarium

import android.app.Application
import ru.distillarium.content.ContentUpdateScheduler

class DistillariumApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Инициализация контейнера до планировщика: Worker возьмёт контент отсюда.
        container
        ContentUpdateScheduler.schedule(this)
    }
}
