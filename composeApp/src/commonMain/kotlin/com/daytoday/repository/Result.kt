package com.daytoday.repository

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val error: String, val throwable: Throwable?) : Result<Nothing>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> failure(error: String, throwable: Throwable? = null): Result<T> = Failure(error, throwable)
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrElse(defaultValue: @UnsafeVariance T): T = (this as? Success)?.data ?: defaultValue

    fun errorOrNull(): String? = (this as? Failure)?.error

    fun exceptionOrNull(): Throwable? = (this as? Failure)?.throwable

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw (throwable ?: IllegalStateException(error))
    }

    fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    fun onFailure(action: (Failure) -> Unit): Result<T> {
        if (this is Failure) action(this)
        return this
    }

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    fun <R> mapCatching(transform: (T) -> R): Result<R> = try {
        when (this) {
            is Success -> Success(transform(data))
            is Failure -> this
        }
    } catch (e: Throwable) {
        Failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
    }

    fun <R> fold(onSuccess: (T) -> R, onFailure: (Failure) -> R): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(this)
    }

    fun toList(): List<T> = when (this) {
        is Success -> listOf(data)
        is Failure -> emptyList()
    }
}
