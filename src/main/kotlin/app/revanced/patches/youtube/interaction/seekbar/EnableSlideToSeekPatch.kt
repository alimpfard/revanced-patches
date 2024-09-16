package app.revanced.patches.youtube.interaction.seekbar

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.all.misc.resources.AddResourcesPatch
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import app.revanced.patches.youtube.interaction.seekbar.fingerprints.DoubleSpeedSeekNoticeFingerprint
import app.revanced.patches.youtube.interaction.seekbar.fingerprints.SlideToSeekFingerprint
import app.revanced.patches.youtube.misc.integrations.IntegrationsPatch
import app.revanced.patches.youtube.misc.settings.SettingsPatch
import app.revanced.patches.youtube.misc.playservice.YouTubeVersionCheck
import app.revanced.util.getReference
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Enable slide to seek",
    description = "Adds an option to enable slide to seek instead of playing at 2x speed when pressing and holding in the video player. Including this patch may cause issues with tapping or double tapping the video player overlay.",
    dependencies = [IntegrationsPatch::class, SettingsPatch::class, AddResourcesPatch::class],
    compatiblePackages = [
        CompatiblePackage(
            "com.google.android.youtube",
            [
                "18.43.45",
                "18.44.41",
                "18.45.43",
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
                "19.25.37",
                "19.34.42",
            ]
        )
    ],
    use = false
)
@Suppress("unused")
object EnableSlideToSeekPatch : BytecodePatch(
    setOf(
        SlideToSeekFingerprint,
        DoubleSpeedSeekNoticeFingerprint
    )
) {
    private const val INTEGRATIONS_CLASS_DESCRIPTOR = "Lapp/revanced/integrations/youtube/patches/SlideToSeekPatch;"

    override fun execute(context: BytecodeContext) {
        AddResourcesPatch(this::class)

        SettingsPatch.PreferenceScreen.SEEKBAR.addPreferences(
            SwitchPreference("revanced_slide_to_seek")
        )

        // Restore the behaviour to slide to seek.
        SlideToSeekFingerprint.resultOrThrow().let {
            val checkIndex = it.scanResult.patternScanResult!!.startIndex
            val checkReference = it.mutableMethod
                .getInstruction(checkIndex).getReference<MethodReference>()!!.toString()

            // A/B check method was only called on this class.
            it.mutableClass.methods.forEach { method ->
                method.implementation!!.instructions!!.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@forEachIndexed

                    val reference = (instruction as Instruction35c).reference as MethodReference
                    if (reference.toString() != checkReference) return@forEachIndexed

                    method.replaceInstruction(index,
                        "invoke-static { }, $INTEGRATIONS_CLASS_DESCRIPTOR->isSlideToSeekDisabled()Z "
                    )
                }
            }
        }

        // Disable the double speed seek notice.
        // 19.17.41 is the last version with this code.
        if (!YouTubeVersionCheck.is_19_17_or_greater) {
            DoubleSpeedSeekNoticeFingerprint.resultOrThrow().let {
                it.mutableMethod.apply {
                    val insertIndex = it.scanResult.patternScanResult!!.endIndex + 1
                    val targetRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA

                    addInstructions(
                        insertIndex,
                        """
                            invoke-static { }, $INTEGRATIONS_CLASS_DESCRIPTOR->isSlideToSeekDisabled()Z
                            move-result v$targetRegister
                        """
                    )
                }
            }
        }
    }
}