package com.que.platform.android.llm

import com.que.core.service.Message
import com.que.core.service.Role

/**
 * Formats conversation messages into model-specific prompt strings.
 * Each LLM family requires a different chat template for proper instruction following.
 */
object ChatTemplateFormatter {

    fun format(messages: List<Message>, template: ChatTemplate): String {
        return when (template) {
            ChatTemplate.GEMMA -> formatGemma(messages)
            ChatTemplate.LLAMA3 -> formatLlama3(messages)
            ChatTemplate.CHATML -> formatChatML(messages)
            ChatTemplate.PHI3 -> formatPhi3(messages)
        }
    }

    private fun formatGemma(messages: List<Message>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.MODEL -> "model"
                Role.SYSTEM -> "user"
            }
            sb.append("<start_of_turn>$role\n")
            sb.append(msg.content)
            sb.append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun formatLlama3(messages: List<Message>): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        for (msg in messages) {
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.MODEL -> "assistant"
                Role.SYSTEM -> "system"
            }
            sb.append("<|start_header_id|>${role}<|end_header_id|>\n\n")
            sb.append(msg.content)
            sb.append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatChatML(messages: List<Message>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.MODEL -> "assistant"
                Role.SYSTEM -> "system"
            }
            sb.append("<|im_start|>$role\n")
            sb.append(msg.content)
            sb.append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatPhi3(messages: List<Message>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            val tag = when (msg.role) {
                Role.USER -> "<|user|>"
                Role.MODEL -> "<|assistant|>"
                Role.SYSTEM -> "<|system|>"
            }
            sb.append("$tag\n")
            sb.append(msg.content)
            sb.append("<|end|>\n")
        }
        sb.append("<|assistant|>\n")
        return sb.toString()
    }
}
