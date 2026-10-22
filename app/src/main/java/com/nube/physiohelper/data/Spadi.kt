package com.nube.physiohelper.data

/**
 * Items of the Shoulder Pain and Disability Index (SPADI), scored 0–10.
 * First 5 are the pain subscale, the remaining 8 the disability subscale.
 */
object Spadi {
    val painItems = listOf(
        "Pain at its worst",
        "Lying on the right side",
        "Reaching for something on a high shelf",
        "Touching the back of your neck",
        "Pushing with the right arm",
    )
    val disabilityItems = listOf(
        "Washing your hair",
        "Washing your back",
        "Putting on an undershirt or pullover",
        "Putting on a shirt that buttons down the front",
        "Putting on your trousers",
        "Placing an object on a high shelf",
        "Carrying a heavy object (about 5 kg)",
        "Removing something from your back pocket",
    )
    val items = painItems + disabilityItems
}
