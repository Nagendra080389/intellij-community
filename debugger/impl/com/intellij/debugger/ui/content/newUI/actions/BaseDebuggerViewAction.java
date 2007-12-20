package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.Nullable;

public abstract class BaseDebuggerViewAction extends AnAction {

  public final void update(final AnActionEvent e) {
    ViewContext context = getViewFacade(e);
    Content[] content = getContent(e);

    if (context != null && content != null) {
      update(e, context, content);
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected void update(AnActionEvent e, ViewContext context, Content[] content) {

  }

  public final void actionPerformed(final AnActionEvent e) {
    actionPerformed(e, getViewFacade(e), getContent(e));
  }


  protected abstract void actionPerformed(AnActionEvent e, ViewContext context, Content[] content);
  

  @Nullable
  private ViewContext getViewFacade(final AnActionEvent e) {
    return e.getData(ViewContext.CONTEXT_KEY);
  }

  @Nullable
  private Content[] getContent(final AnActionEvent e) {
    return e.getData(ViewContext.CONTENT_KEY);
  }

  protected static boolean isDetached(ViewContext context, Content content) {
    return context.findCellFor(content).isDetached();
  }

  protected static Tab getTabFor(final ViewContext context, final Content[] content) {
    Grid grid = context.findGridFor(content[0]);
    Tab tab = context.getTabFor(grid);
    return tab;
  }

  protected final void setEnabled(AnActionEvent e, boolean enabled) {
    e.getPresentation().setVisible(enabled);
  }
}
