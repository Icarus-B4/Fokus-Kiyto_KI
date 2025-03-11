package com.deepcore.kiytoapp.ai

import com.deepcore.kiytoapp.data.Task
import java.util.*

data class TaskAnalysis(
    val productivityScore: Float, // 0.0 bis 1.0
    val focusRecommendation: String,
    val taskOptimizationTips: String,
    val detailedRecommendations: List<String>
)

object TaskAnalyzer {
    fun analyzeTaskPatterns(tasks: List<Task>): TaskAnalysis {
        val completedTasks = tasks.filter { it.completed }
        val pendingTasks = tasks.filter { !it.completed }
        val highPriorityTasks = pendingTasks.filter { it.priority == com.deepcore.kiytoapp.data.Priority.HIGH }
        val overdueTasks = pendingTasks.filter { 
            it.dueDate?.before(Date()) ?: false 
        }
        
        // Berechne Produktivitätsscore
        val productivityScore = calculateProductivityScore(tasks)
        
        // Generiere Fokus-Empfehlungen
        val focusRecommendation = generateFocusRecommendation(tasks)
        
        // Generiere Aufgabenoptimierungs-Tipps
        val taskOptimizationTips = generateTaskOptimizationTips(tasks)
        
        // Generiere detaillierte Empfehlungen
        val detailedRecommendations = generateDetailedRecommendations(tasks)
        
        return TaskAnalysis(
            productivityScore = productivityScore,
            focusRecommendation = focusRecommendation,
            taskOptimizationTips = taskOptimizationTips,
            detailedRecommendations = detailedRecommendations
        )
    }

    private fun calculateProductivityScore(tasks: List<Task>): Float {
        if (tasks.isEmpty()) return 0f
        
        val completedTasks = tasks.count { it.completed }
        val totalTasks = tasks.size
        
        return completedTasks.toFloat() / totalTasks
    }

    private fun generateFocusRecommendation(tasks: List<Task>): String {
        val pendingHighPriorityTasks = tasks.filter { !it.completed && it.priority == com.deepcore.kiytoapp.data.Priority.HIGH }
        
        return when {
            pendingHighPriorityTasks.isNotEmpty() -> {
                "Konzentrieren Sie sich auf die ${pendingHighPriorityTasks.size} wichtigen Aufgaben. " +
                "Empfohlen wird eine Fokus-Session von 25 Minuten für: ${pendingHighPriorityTasks.first().title}"
            }
            tasks.any { !it.completed } -> {
                "Planen Sie regelmäßige Fokus-Sessions ein, um Ihre Aufgaben effizient zu erledigen."
            }
            else -> {
                "Gut gemacht! Alle Aufgaben sind erledigt. Nutzen Sie die Zeit für Planung und Reflexion."
            }
        }
    }

    private fun generateTaskOptimizationTips(tasks: List<Task>): String {
        val pendingTasks = tasks.filter { !it.completed }
        val overdueTasks = pendingTasks.filter { 
            it.dueDate?.before(Date()) ?: false 
        }
        
        return when {
            overdueTasks.isNotEmpty() -> {
                "Es gibt ${overdueTasks.size} überfällige Aufgaben. Priorisieren Sie diese und teilen Sie sie in kleinere Schritte auf."
            }
            pendingTasks.size > 5 -> {
                "Sie haben viele offene Aufgaben. Fokussieren Sie sich auf die wichtigsten 3 Aufgaben und delegieren Sie wenn möglich."
            }
            pendingTasks.isEmpty() -> {
                "Alle Aufgaben sind erledigt. Planen Sie Ihre nächsten Ziele und erstellen Sie neue Aufgaben."
            }
            else -> {
                "Ihre Aufgabenliste ist gut organisiert. Behalten Sie den Überblick und aktualisieren Sie Prioritäten regelmäßig."
            }
        }
    }

    private fun generateDetailedRecommendations(tasks: List<Task>): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Analysiere Aufgabenmuster
        val completedTasks = tasks.filter { it.completed }
        val pendingTasks = tasks.filter { !it.completed }
        val highPriorityTasks = pendingTasks.filter { it.priority == com.deepcore.kiytoapp.data.Priority.HIGH }
        
        // Generiere spezifische Empfehlungen
        if (highPriorityTasks.isNotEmpty()) {
            recommendations.add("Priorisieren Sie: ${highPriorityTasks.joinToString(", ") { it.title }}")
        }
        
        if (pendingTasks.size > 5) {
            recommendations.add("Teilen Sie große Aufgaben in kleinere Teilaufgaben auf")
        }
        
        if (completedTasks.isNotEmpty()) {
            recommendations.add("Reflektieren Sie über erfolgreich abgeschlossene Aufgaben und übertragen Sie bewährte Strategien")
        }
        
        // Zeitmanagement-Empfehlungen
        recommendations.add("Planen Sie Fokus-Zeiten für konzentriertes Arbeiten ein")
        recommendations.add("Nutzen Sie die Pomodoro-Technik: 25 Minuten Arbeit, 5 Minuten Pause")
        
        return recommendations
    }
} 