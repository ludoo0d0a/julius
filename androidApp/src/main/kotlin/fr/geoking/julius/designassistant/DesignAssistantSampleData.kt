package fr.geoking.julius.designassistant

object DesignAssistantSampleData {
    val aeroFlow = DesignProject(
        id = "aeroflow",
        name = "AeroFlow Website",
        emoji = "✈️",
        activeFeaturesCount = 4,
        promptCount = 12,
        mainBranch = "main",
        lastModifiedLabel = "Il y a 2h",
        description = "Site vitrine et catalogue produits pour la marque AeroFlow.",
        features = listOf(
            DesignFeature("auth", "User Authentication", FeatureStatus.IN_PROGRESS),
            DesignFeature("catalog", "Product Catalog", FeatureStatus.DONE, "feat/catalog-carousel", 12, "Catalog Refresh"),
            DesignFeature("contact", "Contact Form", FeatureStatus.TODO),
            DesignFeature("checkout", "Checkout Flow", FeatureStatus.IN_PROGRESS),
        ),
    )

    val eCommerce = DesignProject(
        id = "ecommerce",
        name = "E-Commerce App",
        emoji = "📦",
        activeFeaturesCount = 4,
        promptCount = 12,
        mainBranch = "main",
        lastModifiedLabel = "Il y a 2h",
        description = "Application mobile e-commerce avec paiement et OAuth.",
        features = listOf(
            DesignFeature("oauth", "OAuth", FeatureStatus.READY, "feature/oauth-auth", 42, "OAuth social login"),
            DesignFeature("cart", "Panier", FeatureStatus.IN_PROGRESS),
            DesignFeature("payments", "Paiements", FeatureStatus.IDEA),
            DesignFeature("catalog", "Catalogue", FeatureStatus.DONE, "feat/catalog-v2", 38, "Catalog v2"),
        ),
    )

    val projects = listOf(eCommerce, aeroFlow)

    val catalogChatMessages = listOf(
        DesignChatMessage("u1", ChatMessageKind.USER, "Peux-tu ajouter un carrousel sur la page catalogue ?"),
        DesignChatMessage(
            "a1",
            ChatMessageKind.AGENT,
            "Bien sûr ! Je vais générer un composant carousel responsive pour Product Catalog.",
        ),
        DesignChatMessage(
            "c1",
            ChatMessageKind.CODE,
            "Carousel HTML",
            codeSnippet = """<section class="catalog-carousel">
  <div class="slide" data-index="0">...</div>
</section>""",
        ),
        DesignChatMessage(
            "a2",
            ChatMessageKind.AGENT,
            "J'ai généré le code et mis à jour la branche feat/catalog-carousel.",
            branch = "feat/catalog-carousel",
            prNumber = 12,
            prTitle = "Catalog Refresh",
        ),
        DesignChatMessage(
            "ci1",
            ChatMessageKind.CI,
            "La CI a validé les tests unitaires — PR verte ✅",
        ),
    )

    val oauthChatMessages = listOf(
        DesignChatMessage("u1", ChatMessageKind.USER, "Implémente OAuth Google et GitHub pour l'app."),
        DesignChatMessage(
            "a1",
            ChatMessageKind.AGENT,
            "Je prépare le flux OAuth sur la branche feature/oauth-auth.",
            branch = "feature/oauth-auth",
            prNumber = 42,
            prTitle = "OAuth social login",
        ),
        DesignChatMessage(
            "ci1",
            ChatMessageKind.CI,
            "Pipeline CI terminé — tous les jobs sont au vert ✅",
        ),
    )

    val promptShortcuts = listOf(
        "Générer les tests unitaires",
        "Corriger les bugs",
        "Créer la PR",
        "Résumer les changements",
    )

    val generatedCodeSample = """
        // OAuthProvider.kt
        class OAuthProvider(private val config: OAuthConfig) {
            suspend fun signIn(provider: Provider): AuthResult { ... }
        }
    """.trimIndent()

    val modifiedFilesSample = listOf(
        "app/src/main/auth/OAuthProvider.kt",
        "app/src/main/auth/OAuthConfig.kt",
        "app/src/main/res/values/strings_auth.xml",
    )
}
