package com.lumaos.pro.launcher.vault.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind

/**
 * Private gallery.
 *
 * Thumbnails are decoded from ciphertext into memory, on demand, and dropped when
 * the vault locks - there is no thumbnail directory, no `.thumb` cache and no
 * MediaStore entry anywhere in this path. Videos can be imported and played, and
 * are marked with a badge instead of being decoded for a poster frame (decoding a
 * video frame would need a plaintext scratch file).
 */
class VaultGalleryActivity : VaultScreenActivity() {

    private lateinit var grid: GridView
    private lateinit var empty: View
    private var items: List<VaultItem> = emptyList()

    override fun onCreateContent(): View {
        val container = VaultUi.column(this)
        container.setBackgroundColor(VaultDesign.colorBackgroundTop)
        val content = VaultUi.column(this).apply {
            setPadding(
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.screenMarginHorizontal),
                0
            )
            addView(header("Gallery", null) { finish() })
        }
        container.addView(content)

        grid = GridView(this).apply {
            numColumns = 3
            horizontalSpacing = VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceXXS)
            verticalSpacing = VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceXXS)
            clipToPadding = false
            setPadding(
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceXS),
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultGalleryActivity, VaultDesign.spaceXXL)
            )
            adapter = ThumbAdapter()
            setOnItemClickListener { _, _, position, _ ->
                val item = items.getOrNull(position) ?: return@setOnItemClickListener
                startActivity(Intent(this@VaultGalleryActivity, VaultViewerActivity::class.java).putExtra(VaultViewerActivity.EXTRA_ID, item.id))
            }
            setOnItemLongClickListener { _, _, position, _ ->
                items.getOrNull(position)?.let { itemOptions(it) }
                true
            }
        }
        container.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        empty = VaultUi.emptyState(
            this,
            "Nothing here yet",
            "Photos and videos you take with the private camera, or import into the Vault, appear here - and nowhere else on this phone."
        )
        container.addView(empty)
        reload()
        return container
    }

    override fun onResume() {
        super.onResume()
        if (manager.session.isUnlocked) reload()
    }

    private fun reload() {
        items = manager.items(setOf(VaultItemKind.IMAGE, VaultItemKind.VIDEO))
        (grid.adapter as? BaseAdapter)?.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun itemOptions(item: VaultItem) {
        val options = arrayOf("Open", "Rename", "Export a copy", "Delete")
        android.app.AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, VaultViewerActivity::class.java).putExtra(VaultViewerActivity.EXTRA_ID, item.id)
                    )
                    1 -> promptRename(item)
                    2 -> startExport(item)
                    3 -> VaultActions.confirmDelete(this, manager, item) { reload() }
                }
            }
            .show()
    }

    override fun onExportFinished(item: VaultItem) = reload()

    override fun onImportFinished(count: Int) = reload()

    private fun promptRename(item: VaultItem) {
        VaultActions.promptRename(this, manager, item) { reload() }
    }

    private inner class ThumbAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val size = (resources.displayMetrics.widthPixels - VaultDesign.dp(this@VaultGalleryActivity, 48f)) / 3
            val cell = (convertView as? FrameLayout) ?: FrameLayout(this@VaultGalleryActivity).apply {
                layoutParams = android.widget.AbsListView.LayoutParams(size, size)
            }
            cell.removeAllViews()
            cell.setBackgroundColor(VaultDesign.colorSurface)

            val image = ImageView(this@VaultGalleryActivity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cell.addView(image)

            manager.thumbnail(item, maxOf(160, size))?.let { bitmap: Bitmap ->
                image.setImageBitmap(bitmap)
            } ?: run {
                image.visibility = View.GONE
                cell.addView(VaultUi.text(
                    this@VaultGalleryActivity,
                    item.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    VaultDesign.typeMicroSp,
                    VaultDesign.colorTextTertiary
                ).apply { gravity = Gravity.CENTER })
            }

            if (item.kind == VaultItemKind.VIDEO) {
                cell.addView(VaultUi.text(
                    this@VaultGalleryActivity,
                    "▶",
                    VaultDesign.typeHeadingSp,
                    VaultDesign.colorTextPrimary,
                    VaultUi.Weight.SEMIBOLD
                ).apply {
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
            }

            val badge = TextView(this@VaultGalleryActivity).apply {
                text = formatSize(item.sizeBytes)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, VaultDesign.typeMicroSp - 1f)
                setTextColor(Color.WHITE)
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
            }
            cell.addView(badge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply { setMargins(0, 0, VaultDesign.dp(this@VaultGalleryActivity, 6f), VaultDesign.dp(this@VaultGalleryActivity, 4f)) })
            return cell
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
