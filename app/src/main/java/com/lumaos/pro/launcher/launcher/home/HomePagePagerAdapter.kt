package com.lumaos.pro.launcher.launcher.home

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.launcher.model.HomeItem

/**
 * Horizontal desktop pages. Each snapped page owns a bounded vertical grid;
 * validated pages fit the viewport and never scroll vertically. Legacy pages
 * that predate grid validation keep a scroll fallback so old placements remain reachable.
 */
class HomePagePagerAdapter(
    initialGrid: RecyclerView,
    initialAdapter: HomeGridAdapter,
    private val createGrid: () -> RecyclerView,
    private val createAdapter: (pageIndex: Int) -> HomeGridAdapter,
    private val onGridBound: (pageIndex: Int, grid: RecyclerView, adapter: HomeGridAdapter) -> Unit
) : RecyclerView.Adapter<HomePagePagerAdapter.PageHolder>() {

    private val pageGrids = HashMap<Int, RecyclerView>()
    private val pageAdapters = mutableListOf(initialAdapter)
    private var pages: List<List<HomeItem>> = listOf(emptyList())
    private var firstGrid: RecyclerView? = initialGrid

    init {
        setHasStableIds(false)
        initialGrid.adapter = initialAdapter
    }

    class PageHolder(val grid: RecyclerView) : RecyclerView.ViewHolder(grid)

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val grid = firstGrid?.also { firstGrid = null } ?: createGrid()
        grid.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PageHolder(grid)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        while (pageAdapters.size <= position) pageAdapters += createAdapter(pageAdapters.size)
        val adapter = pageAdapters[position]
        if (holder.grid.adapter !== adapter) holder.grid.adapter = adapter
        pageGrids.entries.removeAll { it.value === holder.grid }
        pageGrids[position] = holder.grid
        adapter.submit(pages[position])
        onGridBound(position, holder.grid, adapter)
    }

    override fun onViewRecycled(holder: PageHolder) {
        val position = pageGrids.entries.firstOrNull { it.value === holder.grid }?.key
        if (position != null) pageGrids.remove(position)
        super.onViewRecycled(holder)
    }

    fun submitPages(newPages: List<List<HomeItem>>) {
        pages = newPages.ifEmpty { listOf(emptyList()) }
        while (pageAdapters.size > pages.size) pageAdapters.removeAt(pageAdapters.lastIndex)
        pageAdapters.forEachIndexed { index, adapter ->
            if (index < pages.size) adapter.submit(pages[index])
        }
        notifyDataSetChanged()
    }

    fun gridAt(index: Int): RecyclerView? = pageGrids[index]
    fun adapterAt(index: Int): HomeGridAdapter? = pageAdapters.getOrNull(index)
    fun visibleGrids(): List<RecyclerView> = pageGrids.values.toList()
    fun forEachAdapter(action: (HomeGridAdapter) -> Unit) = pageAdapters.forEach(action)
}
