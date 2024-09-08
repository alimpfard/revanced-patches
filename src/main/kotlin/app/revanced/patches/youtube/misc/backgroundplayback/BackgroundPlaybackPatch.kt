package app.revanced.patches.youtube.misc.backgroundplayback

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.youtube.misc.backgroundplayback.fingerprints.BackgroundPlaybackManagerFingerprint
import app.revanced.patches.youtube.misc.backgroundplayback.fingerprints.BackgroundPlaybackSettingsFingerprint
import app.revanced.patches.youtube.misc.backgroundplayback.fingerprints.KidsBackgroundPlaybackPolicyControllerFingerprint
import app.revanced.patches.youtube.misc.integrations.IntegrationsPatch
import app.revanced.patches.youtube.misc.playertype.PlayerTypeHookPatch
import app.revanced.patches.youtube.misc.settings.SettingsPatch
import app.revanced.patches.youtube.video.information.VideoInformationPatch
import app.revanced.util.findOpcodeIndicesReversed
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Remove background playback restrictions",
    description = "Removes restrictions on background playback, including playing kids videos in the background.",
    dependencies = [
        BackgroundPlaybackResourcePatch::class,
        IntegrationsPatch::class,
        PlayerTypeHookPatch::class,
        VideoInformationPatch::class,
        SettingsPatch::class,
    ],
    compatiblePackages = [
        CompatiblePackage(
            "com.google.android.youtube",
            [
                "18.48.39",
                "18.49.37",
                "19.01.34",
                "19.02.39",
                "19.03.36",
                "19.04.38",
                "19.05.36",
                "19.06.39",
                "19.07.40",
                "19.08.36",
                "19.09.38",
                "19.10.39",
                "19.11.43",
                "19.12.41",
                "19.13.37",
                "19.14.43",
                "19.15.36",
                "19.16.39",
                "19.17.41",
                "19.18.41",
                "19.19.39",
                "19.20.35",
                "19.21.40",
                "19.22.43",
                "19.23.40",
                "19.24.45",
                "19.25.37", 
                "19.26.42",
                "19.28.42",
                "19.29.42",
                "19.30.39",
            ]
        )
    ]
)
@Suppress("unused")
object BackgroundPlaybackPatch : BytecodePatch(
    setOf(
        BackgroundPlaybackManagerFingerprint,
        BackgroundPlaybackSettingsFingerprint,
        KidsBackgroundPlaybackPolicyControllerFingerprint
    )
) {
    private const val INTEGRATIONS_CLASS_DESCRIPTOR =
        "Lapp/revanced/integrations/youtube/patches/BackgroundPlaybackPatch;"

    override fun execute(context: BytecodeContext) {
        BackgroundPlaybackManagerFingerprint.resultOrThrow().mutableMethod.apply {
            findOpcodeIndicesReversed(Opcode.RETURN).forEach{ index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA

                // Replace to preserve control flow label.
                replaceInstruction(
                    index,
                    "invoke-static { v$register }, $INTEGRATIONS_CLASS_DESCRIPTOR->allowBackgroundPlayback(Z)Z"
                )

                addInstructions(index + 1,
                    """
                       move-result v$register
                       return v$register
                    """
                )
            }
        }

        // Enable background playback option in YouTube settings
        BackgroundPlaybackSettingsFingerprint.resultOrThrow().mutableMethod.apply {
            val booleanCalls = implementation!!.instructions.withIndex()
                .filter { ((it.value as? ReferenceInstruction)?.reference as? MethodReference)?.returnType == "Z" }

            val settingsBooleanIndex = booleanCalls.elementAt(1).index
            val settingsBooleanMethod =
                context.toMethodWalker(this).nextMethod(settingsBooleanIndex, true).getMethod() as MutableMethod

            settingsBooleanMethod.addInstructions(
                0,
                """
                    invoke-static {}, $INTEGRATIONS_CLASS_DESCRIPTOR->overrideBackgroundPlaybackAvailable()Z
                    move-result v0
                    return v0
                """
            )
        }

        // Force allowing background play for videos labeled for kids.
        KidsBackgroundPlaybackPolicyControllerFingerprint.resultOrThrow().mutableMethod.addInstruction(
            0,
            "return-void"
        )
    }
}
