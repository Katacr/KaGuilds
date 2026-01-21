package org.katacr.kaguilds.service

sealed class OperationResult {
    // 通用成功
    object Success : OperationResult()

    // 权限与状态错误
    object NoPermission : OperationResult()
    object NotInGuild : OperationResult()
    object AlreadyInGuild : OperationResult()

    // 经济相关：携带“当前余额”或“还差多少”
    data class InsufficientFunds(val required: Double) : OperationResult()

    object NameAlreadyExists : OperationResult()

    // 系统错误
    data class Error(val message: String) : OperationResult()
}