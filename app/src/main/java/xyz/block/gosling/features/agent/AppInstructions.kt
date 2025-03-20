package xyz.block.gosling.features.agent

/**
 * Store and retrieve per app instructions.
 */
object AppInstructions {
    /**
     * Map of package names to their corresponding instructions.
     * Each instruction is a multiline string providing guidance for the specific app.
     */
    private val appInstructionsMap = mapOf(
        // Maps apps
        "com.google.android.apps.maps" to """            
            First think if the user wants to:
            1. Find a specific location
            2. Get directions between locations
            3. Explore nearby places
        IMPORTANT: don't just search for A to B, but search for separate places, add stops if needed.
                   Map search is not general search. There may be locations user is asking for mixed in with general place (like a vegan restaurant)
        """.trimIndent(),

        "com.waze" to """
            Waze is a community-driven navigation app focused on real-time traffic updates.
            
            Key features:
            - GPS navigation with real-time traffic updates
            - Community-reported hazards, police, and road closures
            - Find the cheapest gas stations along your route
            - Estimated arrival times based on live traffic data
            - Integration with music apps and calendar appointments
            
            When using Waze, help the user:
            1. Enter a destination (use the search bar)
            2. Choose the best route based on current conditions
            3. Report or view road hazards and traffic conditions
            4. Find gas stations or other stops along the route
        """.trimIndent(),
        
        // Amazon app
        "com.amazon.mShop.android.shopping" to """
            Amazon Shopping is an e-commerce app for browsing and purchasing products and resaerching.
                        
            When using Amazon:
            1. Use the search bar to find specific products
            2. Browse categories by tapping the menu icon
            3. Check product details thoroughly before adding to cart
            4. Review the cart before proceeding to checkout
            5. Select shipping options and payment methods during checkout
        """.trimIndent(),
        

        // Ride-sharing (Uber)
        "com.ubercab" to """
            Uber is a ride-sharing app that connects riders with drivers.
            
            Key features:
            - Request rides to specific destinations
            - Choose from different ride options (UberX, Comfort, Black, etc.)
            - View estimated fare and arrival time before confirming
            - Track driver's location in real-time
            - Rate drivers and provide feedback after rides
            - View ride history and receipts
            
            When using Uber:
            1. Enter destination in the "Where to?" field
            2. Select the type of ride desired
            3. Confirm pickup location (adjust if needed)
            4. Review price estimate and confirm ride
            5. Track driver's approach and communicate if necessary
            6. Verify driver and vehicle details before entering
        """.trimIndent(),
        

        // Add more package name to instructions mappings as needed
    )

    /**
     * Retrieves instructions for a specific app based on its package name.
     * If no exact match is found, tries to match based on package name patterns.
     *
     * @param packageName The package name of the app
     * @return The instructions as a string, or null if no instructions exist for the package
     */
    fun getInstructions(packageName: String): String? {
        // First try exact match
        appInstructionsMap[packageName]?.let { return it }
        
        // If no exact match, try pattern matching
        val lowercasePackage = packageName.lowercase()
        
        return when {
            // Maps apps
            lowercasePackage.contains("map") || 
            lowercasePackage.contains("navigation") || 
            lowercasePackage.contains("gps") -> """
                This appears to be a mapping or navigation app.
                
                When using mapping apps:
                1. First determine if the user wants to find a location or get directions
                2. Use the search bar to enter addresses or place names
                3. For directions, enter both starting point and destination
                4. Look for options to change transportation mode (driving, walking, transit)
                5. Check for additional features like traffic information or nearby places
                
                IMPORTANT: Don't just search for A to B, but search for separate places first.
                Add stops if needed. Map search is not general search.
            """.trimIndent()
            
            // Shopping apps
            lowercasePackage.contains("shop") || 
            lowercasePackage.contains("store") || 
            lowercasePackage.contains("market") || 
            lowercasePackage.contains("buy") -> """
                This appears to be a shopping or e-commerce app.
                
                When using shopping apps:
                1. Use the search bar to find specific products
                2. Browse categories if available
                3. Check product details, prices, and reviews before adding to cart
                4. Review cart contents before checkout
                5. Look for shipping options and payment methods during checkout
            """.trimIndent()
            
            // Social media apps
            lowercasePackage.contains("social") || 
            lowercasePackage.contains("chat") || 
            lowercasePackage.contains("message") || 
            lowercasePackage.contains("community") -> """
                This appears to be a social media or messaging app.
                This can be used for research or answering a user query                
            """.trimIndent()
            
            // Food delivery apps
            lowercasePackage.contains("food") || 
            lowercasePackage.contains("delivery") || 
            lowercasePackage.contains("order") || 
            lowercasePackage.contains("restaurant") -> """
                This appears to be a food delivery or restaurant app.
                
                When using food delivery apps:
                1. Confirm location or delivery address
                2. Browse restaurants or search for specific cuisines
                3. View menus and select items
                4. Customize orders as needed
                5. Review order before checkout
                6. Select payment method and complete order
                7. Track delivery status after ordering
            """.trimIndent()
            
            // Transportation/ride-sharing apps
            lowercasePackage.contains("ride") || 
            lowercasePackage.contains("taxi") || 
            lowercasePackage.contains("transit") || 
            lowercasePackage.contains("transport") -> """
                This appears to be a transportation or ride-sharing app.
                
                When using transportation apps:
                1. Enter destination in the search field
                2. Confirm pickup location
                3. Review available ride options and prices
                4. Select preferred option and confirm ride
                5. Track vehicle approach and verify driver information
                6. Check for payment options and ride history features
            """.trimIndent()
            
            // Banking/payment apps
            lowercasePackage.contains("bank") || 
            lowercasePackage.contains("pay") || 
            lowercasePackage.contains("finance") || 
            lowercasePackage.contains("money") -> """
                This appears to be a banking or payment app.
                
                When using banking/payment apps:
                1. Check for account balance and transaction history
                2. For sending money: Look for transfer or payment options
                3. For receiving money: Look for request money features
                4. Verify recipient information carefully before confirming transactions
                5. Check for security features like biometric authentication
                6. Look for bill payment or scheduled payment options if needed
            """.trimIndent()

            else -> {
                // finally we will see if there is more general advice
                val category = IntentAppKinds.getCategoryForPackage(packageName)
                if (category != null && category.generalUsageInstructions.isNotEmpty()) {
                    return category.generalUsageInstructions
                } else {
                    return null
                }
            }
        }
    }
}