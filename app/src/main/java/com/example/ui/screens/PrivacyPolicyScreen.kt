package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuraTopBar
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraSlate
import com.example.ui.theme.DiscoveryViolet
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            AuraTopBar(
                title = "Privacy Policy",
                showLogo = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AuraMidnight
                        )
                    }
                }
            )
        },
        containerColor = AuraCrispWhite,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "PRIVACY POLICY",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AuraMidnight,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Aura Media Player",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Effective Date:")
                    }
                    append(" August 15, 2026\n")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Last Updated:")
                    }
                    append(" August 15, 2026")
                },
                fontSize = 14.sp,
                color = AuraSlate,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Aura Media Player (\"Aura,\" \"the App,\" \"we,\" \"us,\" or \"our\") respects your privacy. Aura is designed as a local-first media discovery and intelligence application that processes your personal media and personalization information primarily on your Android device.\n\nThis Privacy Policy explains what information Aura accesses, how that information is used, how it is stored and protected, whether it is transmitted or shared, and how you can delete information associated with the App.",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = AuraMidnight,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            PrivacySection(
                number = "1",
                title = "Developer and Privacy Contact",
                content = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("App:")
                            }
                            append(" Aura Media Player\n\n")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Developer:")
                            }
                            append(" Lance Trimm\n\n")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = DiscoveryViolet)) {
                                append("lancetrimm@gmail.com")
                            }
                        },
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    Text(
                        text = "If you have questions or concerns about this Privacy Policy or Aura's privacy practices, contact us using the privacy contact above.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "2",
                title = "Information Aura Accesses",
                content = {
                    Text(
                        text = "Aura may request access to media stored on your Android device, including photos and videos.\n\nDepending on the features you use, Aura may process information associated with your media, including:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "Photos and videos stored on your device",
                            "Media filenames",
                            "Media metadata",
                            "Media duration and file information",
                            "Media thumbnails and locally generated frames",
                            "Media playback activity",
                            "Favorites",
                            "Ratings",
                            "Skips and other media interactions",
                            "User preferences",
                            "Personalization information",
                            "Taste DNA information",
                            "Information used to generate recommendations and intelligent sorting"
                        )
                    )
                    Text(
                        text = "Aura accesses this information only to provide features and functionality offered by the App.\n\nAura does not require access to your media for unrelated purposes.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "3",
                title = "How Aura Uses Information",
                content = {
                    Text(
                        text = "Aura may use information processed on your device to:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "Display and play your media",
                            "Organize your media library",
                            "Generate thumbnails and previews",
                            "Analyze media for discovery and personalization",
                            "Generate recommendations",
                            "Generate intelligent media sorting",
                            "Maintain your Taste DNA profile",
                            "Remember favorites, ratings, and preferences",
                            "Analyze interactions with media",
                            "Provide local statistics and insights",
                            "Improve the relevance of Aura's discovery experience"
                        )
                    )
                    Text(
                        text = "These activities are performed to provide, maintain, and personalize Aura's functionality.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "4",
                title = "Local-First Processing",
                content = {
                    Text(
                        text = "Aura is designed around a local-first privacy architecture.\n\nFor the consumer version of Aura, the App's media indexing, media analysis, personalization information, interaction history, Taste DNA information, and recommendation data are designed to be processed and stored locally on your Android device.\n\nAura does not require you to upload your personal media library to an Aura server in order to use the App's core media discovery and personalization functionality.\n\nYour personal media remains under your control on your device.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "5",
                title = "Data Transmission",
                content = {
                    Text(
                        text = "The consumer version of Aura is designed so that personal media and locally generated media-intelligence information are not transmitted to Aura servers for advertising, sale, or remote profiling.\n\nAura does not sell your personal information.\n\nAura does not sell your photos or videos.\n\nAura does not provide your personal media library to advertisers.\n\nAura does not use your personal media library for targeted advertising.\n\nIf a future version of Aura introduces functionality that requires transmitting personal or sensitive information from your device, this Privacy Policy will be updated to describe that processing before or when the functionality becomes available, and any consent required by applicable law or Google Play policy will be obtained.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "6",
                title = "Photos and Videos",
                content = {
                    Text(
                        text = "Aura may access photos and videos on your Android device because accessing your media library is necessary for Aura's core functionality.\n\nMedia may be used locally to:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "Display media",
                            "Play media",
                            "Generate thumbnails",
                            "Analyze visual characteristics",
                            "Organize media",
                            "Generate recommendations",
                            "Build personalization information",
                            "Support discovery features"
                        )
                    )
                    Text(
                        text = "Aura does not use your photos or videos for advertising.\n\nAura does not sell your photos or videos.\n\nAura does not intentionally transmit your personal media library to remote servers as part of the consumer version's core functionality.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "7",
                title = "Personalization and Taste DNA",
                content = {
                    Text(
                        text = "Aura may create locally stored personalization information based on how you interact with media.\n\nThis may include information such as:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "Favorites",
                            "Ratings",
                            "Skips",
                            "Playback interactions",
                            "Viewing behavior",
                            "Media preferences",
                            "Taste DNA dimensions",
                            "Recommendation signals",
                            "Other locally generated preference information"
                        )
                    )
                    Text(
                        text = "This information is used to personalize Aura's discovery and recommendation features.\n\nFor the consumer version of Aura, this personalization information is designed to remain on your device.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "8",
                title = "Device Permissions",
                content = {
                    Text(
                        text = "Aura may request Android permissions necessary to access media and provide its functionality.\n\nThe specific permissions requested may vary by Android version and the features available in a particular version of Aura.\n\nYou can review or change Aura's permissions through Android's system settings.\n\nIf you deny or restrict a permission required for a particular feature, that feature may not function properly.\n\nAura does not request permissions for purposes unrelated to the App's functionality.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "9",
                title = "Information Aura Does Not Collect for Remote Use",
                content = {
                    Text(
                        text = "The consumer version of Aura is designed not to remotely collect or maintain a personal profile containing your media library, Taste DNA, or media interaction history.\n\nAura is not designed to sell personal information or personal media.\n\nAura does not use personal media information for advertising.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "10",
                title = "Analytics and Telemetry",
                content = {
                    Text(
                        text = "The consumer version of Aura is designed without outbound personal-media telemetry.\n\nAura's core intelligence and personalization functionality is designed to operate locally on the device.\n\nIf future versions introduce analytics, crash reporting, telemetry, or other services that transmit information from the device, this Privacy Policy will be updated to accurately describe the information involved, the purpose of the processing, and any applicable third parties.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "11",
                title = "Third-Party Libraries and Services",
                content = {
                    Text(
                        text = "Aura may contain third-party software libraries and Android platform components that are necessary to provide application functionality.\n\nThird-party libraries and services may have their own privacy practices.\n\nAura is responsible for accurately disclosing relevant third-party data handling in accordance with applicable Google Play requirements.\n\nThe presence of a third-party library does not mean that your personal media is automatically transmitted to that third party.\n\nIf a future version of Aura uses a third-party service to process personal or sensitive information, the applicable data handling will be disclosed in accordance with this Privacy Policy and Google Play requirements.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "12",
                title = "Data Storage and Security",
                content = {
                    Text(
                        text = "Aura stores information necessary for its functionality on your Android device.\n\nDepending on the features you use, locally stored information may include:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "Media indexes",
                            "Media metadata",
                            "Thumbnails",
                            "User preferences",
                            "Favorites",
                            "Ratings",
                            "Interaction history",
                            "Taste DNA information",
                            "Recommendation information",
                            "Other application data necessary to provide Aura's functionality"
                        )
                    )
                    Text(
                        text = "Aura uses Android security mechanisms and encrypted local storage where implemented by the App to protect locally stored application information.\n\nNo method of electronic storage or transmission can guarantee absolute security. You are responsible for maintaining appropriate security for your Android device, including device authentication and operating-system security updates.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "13",
                title = "Data Retention",
                content = {
                    Text(
                        text = "Because Aura is designed as a local-first application, locally generated application information may remain on your device until you remove it.\n\nDepending on the type of information, data may be removed when:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "You delete the information using an available Aura feature;",
                            "You remove associated media;",
                            "You clear Aura's application data through Android settings;",
                            "You uninstall Aura; or",
                            "Android otherwise removes the application's stored data."
                        )
                    )
                    Text(
                        text = "Aura does not maintain a remote copy of your personal media library for the consumer version.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            PrivacySection(
                number = "14",
                title = "Data Deletion",
                content = {
                    Text(
                        text = "You can remove locally stored Aura application data through Android's application settings.\n\nUninstalling Aura generally removes the App's locally stored application data according to Android's application-data behavior.\n\nIf Aura provides an in-app control for deleting specific locally stored information, you may use that control to remove the applicable information.\n\nDeleting an item from Aura does not necessarily delete the original media file from your device unless Aura explicitly provides a deletion function and you confirm that action.\n\nBecause the consumer version is designed to keep personal media and personalization data locally on your device, there is generally no remote Aura account database containing your personal media that requires server-side deletion.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "15",
                title = "Accounts",
                content = {
                    Text(
                        text = "The consumer version of Aura does not require a user account for its core local media-management and discovery functionality.\n\nIf a future version introduces account functionality, this Privacy Policy will be updated to describe the information collected through accounts and the process for deleting an account and associated information.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "16",
                title = "Advertising",
                content = {
                    Text(
                        text = "The consumer version of Aura does not use your personal media, Taste DNA, or local media interaction history for targeted advertising.\n\nAura does not sell personal information to advertisers.\n\nIf advertising functionality is introduced in a future version, the applicable data practices will be disclosed in an updated Privacy Policy and Google Play Data Safety disclosure.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "17",
                title = "Children's Privacy",
                content = {
                    Text(
                        text = "Aura is not specifically directed toward children.\n\nAura does not knowingly collect children's personal information through remote servers as part of the consumer version's core functionality.\n\nIf you believe that personal information belonging to a child has been improperly submitted to Aura, please contact us using the privacy contact information provided in this Privacy Policy.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "18",
                title = "Changes to This Privacy Policy",
                content = {
                    Text(
                        text = "We may update this Privacy Policy when:\n\n• Aura's functionality changes;\n• Our privacy practices change;\n• New technologies or services are introduced;\n• Applicable laws or regulations change; or\n• Google Play requirements change.\n\nWhen this happens, we will update the \"Last Updated\" date at the top of this Privacy Policy.\n\nThe current version of this Privacy Policy will be made publicly available at the official Aura Privacy Policy URL.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "19",
                title = "Contact Us",
                content = {
                    Text(
                        text = buildAnnotatedString {
                            append("For questions, concerns, or requests regarding this Privacy Policy or Aura's privacy practices, contact:\n\n")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = DiscoveryViolet)) {
                                append("lancetrimm@gmail.com")
                            }
                        },
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                }
            )

            PrivacySection(
                number = "20",
                title = "Summary",
                content = {
                    Text(
                        text = "Aura Media Player is designed around the principle that your personal media should remain under your control.\n\nThe consumer version of Aura is designed to:",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight
                    )
                    PrivacyBulletList(
                        listOf(
                            "Process personal media locally on your Android device;",
                            "Keep personalization and Taste DNA information locally on your device;",
                            "Avoid selling personal information;",
                            "Avoid using personal media for advertising;",
                            "Avoid requiring remote upload of your personal media library for core functionality; and",
                            "Give you control over locally stored application data through Android's application controls and available Aura features."
                        )
                    )
                    Text(
                        text = "This Privacy Policy describes the intended privacy practices of the consumer version of Aura Media Player as of the effective date shown above.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = AuraMidnight,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PrivacySection(
    number: String,
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "$number. $title",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AuraMidnight,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun PrivacyBulletList(items: List<String>) {
    Column(modifier = Modifier.padding(top = 8.dp, start = 8.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = "•", fontSize = 14.sp, color = AuraMidnight)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = item, fontSize = 14.sp, lineHeight = 22.sp, color = AuraMidnight)
            }
        }
    }
}
