package com.jianji.app.ui.navigation

/** 路由定义。 */
object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val STATISTICS = "statistics"
    const val PROFILE = "profile"

    /** 记一笔 / 编辑账目；transactionId 为空表示新增。 */
    const val ADD_EDIT = "addEdit?transactionId={transactionId}"

    fun addEdit(transactionId: Long? = null): String =
        if (transactionId == null) "addEdit?transactionId=" else "addEdit?transactionId=$transactionId"

    const val CATEGORY_MANAGE = "categoryManage"
    const val ACCOUNT_MANAGE = "accountManage"
    const val BUDGET = "budget"
}
