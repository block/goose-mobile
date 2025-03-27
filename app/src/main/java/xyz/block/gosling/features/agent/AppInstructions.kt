package xyz.block.gosling.features.agent

/**
 * Store and retrieve per app instructions.
 */
object AppInstructions {
    /**
     * Map of package names to their corresponding instructions and URL schemes.
     * Each instruction is a multiline string providing guidance for the specific app.
     */
    private val appInstructionsMap = mapOf(
        // Maps apps
        "com.google.android.apps.maps" to AppInstructionInfo(
            instructions = """            
                First think if the user wants to:
                1. Find a specific location
                2. Get directions between locations
                3. Explore nearby places
                4. Add stops on the way
                IMPORTANT: don't just search for A to B, but search for separate places, add stops if needed.
                           Map search is not general search. There may be locations user is asking for mixed in with general place (like a vegan restaurant)

                URL Schemes:
                - Use 'geo:0,0?q=LOCATION' to search for a location
                - Use 'google.navigation:q=DESTINATION' to start navigation
                - Use 'google.streetview:cbll=LAT,LONG' to view street view at coordinates
                - Use 'google.streetview:panorama_id=ID' to view specific street view locations
            """.trimIndent(),
            urlSchemes = listOf(
                "geo:0,0?q=", // For searching locations
                "google.navigation:q=", // For navigation
                "google.streetview:cbll=", // For street view
                "google.streetview:panorama_id=" // For specific street view locations
            )
        ),

        "com.waze" to AppInstructionInfo(
            instructions = """
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

                URL Schemes:
                - Use 'waze://' to open the main app
                - Use 'waze://place?q=LOCATION' to search for a specific place
                - Use 'waze://nav?q=DESTINATION' to start navigation
                - Use 'waze://search?q=QUERY' to perform a search
            """.trimIndent(),
            urlSchemes = listOf(
                "waze://", // Main Waze URL scheme
                "waze://place", // For specific places
                "waze://nav", // For navigation
                "waze://search" // For search
            )
        ),
        
        // Amazon app
        "com.amazon.mShop.android.shopping" to AppInstructionInfo(
            instructions = """                        
                important instructions on how to use amazon app:
                
                Use the search bar to find specific products, but try variations on the search.
                Don't just search for exact description user provides necessarily, you will need to search for general product sometimes, and then scroll and click in to see if it meets other requirements.
                For example: if looking for stinky fruit, look up fruit first, and then look if any are titled stinky, or description or reviews say are stinky.
                ALWAYS scroll/swipe to look at products, don't just pick the first one
                Click on the product to read information about it, including ratings, may need to click in a few levels.
                If asked, look for specific detailed in descriptions or reviews after clicking in and scrolling
                Consider if the query mentions a specific brand, in which case, ensure items you are reviewing match that specific brand 
                Add items to cart if asked

                URL Schemes:
                - Use 'amzn://' to open the main app
                - Use 'amzn://apps/android' for app-specific features
                - Use 'amzn://gp/product/ASIN' to open a specific product
                - Use 'amzn://gp/search?k=QUERY' to perform a search
            """.trimIndent(),
            urlSchemes = listOf(
                "amzn://", // Main Amazon URL scheme
                "amzn://apps/android", // For app-specific URLs
                "amzn://gp/product", // For product URLs
                "amzn://gp/search" // For search URLs
            )
        ),
        
        // Ride-sharing (Uber)
        "com.ubercab" to AppInstructionInfo(
            instructions = """
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

                URL Schemes:
                - Use 'uber://' to open the main app
                - Use 'uber://request?pickup=LOCATION' to request a ride
                - Use 'uber://riders' to access rider-specific features
                - Use 'uber://history' to view ride history
            """.trimIndent(),
            urlSchemes = listOf(
                "uber://", // Main Uber URL scheme
                "uber://request", // For requesting rides
                "uber://riders", // For rider-specific features
                "uber://history" // For ride history
            )
        )
    )

    /**
     * Retrieves instructions for a specific app based on its package name.
     * If no exact match is found, tries to match based on package name patterns.
     *
     * @param packageName The package name of the app
     * @return The instructions as a string, or null if no instructions exist for the package
     */
    fun getInstructions(packageName: String): String? {
        System.out.println("GETTING INSTRUCTIONS FOR: " + packageName)
        // First try exact match
        appInstructionsMap[packageName]?.let { return it.instructions }
        
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

    /**
     * Retrieves URL schemes for a specific app based on its package name.
     *
     * @param packageName The package name of the app
     * @return List of URL schemes supported by the app, or empty list if none found
     */
    fun getUrlSchemes(packageName: String): List<String> {
        return appInstructionsMap[packageName]?.urlSchemes ?: emptyList()
    }
}

/**
 * Data class to hold both instructions and URL schemes for an app
 */
private data class AppInstructionInfo(
    val instructions: String,
    val urlSchemes: List<String>
)
