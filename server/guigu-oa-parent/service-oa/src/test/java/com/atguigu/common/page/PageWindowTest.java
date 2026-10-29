package com.atguigu.common.page;

import com.atguigu.common.config.exception.GuiguException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageWindowTest {

    @Test
    void rejectsAPageNumberBelowOne() {
        assertThrows(GuiguException.class, () -> PageWindow.of(0L, 10L));
        assertThrows(GuiguException.class, () -> PageWindow.of(-1L, 10L));
        assertThrows(GuiguException.class, () -> PageWindow.of(null, 10L));
    }

    @Test
    void rejectsAPageSizeOutsideOneToTheMaximum() {
        assertThrows(GuiguException.class, () -> PageWindow.of(1L, 0L));
        assertThrows(GuiguException.class, () -> PageWindow.of(1L, -1L));
        assertThrows(GuiguException.class, () -> PageWindow.of(1L, 501L));
        assertThrows(GuiguException.class, () -> PageWindow.of(1L, null));
    }

    @Test
    void keepsTheAcceptedWindowOnThePage() {
        Page<Object> page = PageWindow.of(2L, PageWindow.MAX_LIMIT);

        assertEquals(2, page.getCurrent());
        assertEquals(PageWindow.MAX_LIMIT, page.getSize());
    }
}
