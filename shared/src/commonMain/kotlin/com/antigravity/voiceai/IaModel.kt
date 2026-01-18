package com.antigravity.voiceai

enum class IaModel(val modelName: String, val displayName: String) {
    LLAMA_3_1_SONAR_SMALL("llama-3.1-sonar-small-128k-online", "Sonar Small"),
    LLAMA_3_1_SONAR_LARGE("llama-3.1-sonar-large-128k-online", "Sonar Large"),
    LLAMA_3_1_8B_INSTRUCT("llama-3.1-8b-instruct", "Llama 3.1 Instruct"),
    LLAMA_3_1_70B_INSTRUCT("llama-3.1-70b-instruct", "Llama 3.1 70B Instruct"),
    GEMMA_2_9B_IT("gemma-2-9b-it", "Gemma 2 9B"),
    GEMMA_2_27B_IT("gemma-2-27b-it", "Gemma 2 27B")
}
