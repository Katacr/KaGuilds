package org.katacr.kaguilds.util

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text

/**
 * 消息构建工具类
 * 提供构建可点击文本组件的方法
 */
object MessageUtil {

    /**
     * 构建可点击的文本组件
     *
     * @param text 显示的文字（支持颜色符号 &）
     * @param hoverText 鼠标悬停时显示的文字（支持颜色符号 &）
     * @param command 点击后执行的指令
     * @param newline 是否在组件后添加换行
     * @return 构建好的 TextComponent
     */
    fun createClickableText(
        text: String,
        hoverText: String = "",
        command: String = "",
        newline: Boolean = false
    ): TextComponent {
        val component = TextComponent(translateColorCodes(text))

        // 设置点击事件
        if (command.isNotEmpty()) {
            component.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
        }

        // 设置悬停事件
        if (hoverText.isNotEmpty()) {
            val hover = Text(translateColorCodes(hoverText))
            component.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)
        }

        // 如果需要换行，添加换行符
        if (newline) {
            component.text += "\n"
        }

        return component
    }

    /**
     * 翻译颜色符号
     * 将 & 转换为 §
     *
     * @param text 原始文本
     * @return 转换颜色符号后的文本
     */
    private fun translateColorCodes(text: String): String {
        return text.replace("&", "§")
    }

    /**
     * 创建普通文本组件（支持颜色符号）
     *
     * @param text 显示的文字（支持颜色符号 &）
     * @return 构建好的 TextComponent
     */
    fun createText(text: String): TextComponent {
        return TextComponent(translateColorCodes(text))
    }

}
