package ru.distillarium.core.content

/**
 * Ошибка конвейера контента. Сообщение предназначено пользователю: оно
 * показывается на экране «Обновления» без технических подробностей.
 */
class ContentException(message: String, cause: Throwable? = null) : Exception(message, cause)
