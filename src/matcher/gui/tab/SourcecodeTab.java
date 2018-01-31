package matcher.gui.tab;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import com.sun.javafx.scene.web.skin.HTMLEditorSkin;
import com.sun.javafx.webkit.Accessor;
import com.sun.webkit.WebPage;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import matcher.Util;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;
import matcher.gui.ISelectionProvider;
import matcher.srcremap.SrcRemapper;
import matcher.srcremap.SrcRemapper.ParseException;
import matcher.type.ClassInstance;
import matcher.type.MatchType;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.html.HTMLAnchorElement;

public class SourcecodeTab extends Tab implements IGuiComponent {
	public SourcecodeTab(Gui gui, ISelectionProvider selectionProvider) {
		super("source");

		this.gui = gui;
		this.selectionProvider = selectionProvider;

		init();
	}

	private void init() {
		//text.setEditable(false);
		setContentText("");
		text.getEngine().getLoadWorker().stateProperty().addListener(
				(ov, oldState, newState) -> {
					if (newState == Worker.State.SUCCEEDED) {
						((EventTarget) text.getEngine().getDocument().getDocumentElement()).addEventListener("click", ev -> {
							if (ev.getTarget() instanceof HTMLAnchorElement) {
								System.out.println("Clicked on " + ((HTMLAnchorElement) ev.getTarget()).getHref());
								selectionProvider.linkClicked(((HTMLAnchorElement) ev.getTarget()).getHref());
							}
						}, false);
					}
				});

		setContent(text);
	}

	@Override
	public void onClassSelect(ClassInstance cls) {
		update(cls, false);
	}

	@Override
	public void onMatchChange(Set<MatchType> types) {
		ClassInstance cls = selectionProvider.getSelectedClass();

		if (cls != null) {
			update(cls, true);
		}
	}

	private void update(ClassInstance cls, boolean isRefresh) {
		final int cDecompId = ++decompId;

		if (cls == null) {
			setContentText("Waiting...");
			return;
		}

		int prevScroll = getScrollTop();

		if (!isRefresh) {
			setContentText("decompiling...");
		}

		//Gui.runAsyncTask(() -> gui.getEnv().decompile(cls, true))
		Gui.runAsyncTask(() -> {
			try {
				return SrcRemapper.decorateHTML(gui.getEnv().decompile(cls, true), cls, true);
			} catch (ParseException e){
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				e.printStackTrace();
				return "Error: "+e.getMessage()+"\r\n"+sw.toString()+"\n\n"+e.source;
			} catch (Exception e){
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				e.printStackTrace();
				return "Error: "+e.getMessage()+"\r\n"+sw.toString();
			}
		})
		.whenComplete((res, exc) -> {
			if (cDecompId == decompId) {
				if (exc != null) {
					exc.printStackTrace();

					StringWriter sw = new StringWriter();
					exc.printStackTrace(new PrintWriter(sw));

					if (exc instanceof ParseException) {
						setContentText("parse error: "+sw.toString()+"decompiled source:\n"+((ParseException) exc).source);
					} else {
						setContentText("decompile error: "+sw.toString());
					}

				} else {

					boolean fixScroll = isRefresh && Math.abs(getScrollTop() - prevScroll) < 1;
					if (Util.DEBUG)
						System.out.println("fix scroll: "+fixScroll+", to "+getScrollTop());

					setContentText(res);

					if (fixScroll) webPage.executeScript(webPage.getMainFrame(), "window.scrollTo(0, "+prevScroll+")");
				}
			} else if (exc != null) {
				exc.printStackTrace();
			}
		});
	}

	private final Gui gui;
	private final ISelectionProvider selectionProvider;
	private final WebView text = new WebView();
	private final WebPage webPage = Accessor.getPageFor(text.getEngine());

	private int decompId;

	private void setContentText(String textIn){
		textIn = textIn.replaceAll("(?<!\\\\)<", "&lt;").replaceAll("(?<!\\\\)>", "&gt;")
				.replaceAll("\\\\<", "<").replaceAll("\\\\>", ">");
		webPage.load(webPage.getMainFrame(), "<html><body><pre>"+textIn+"</pre></body></html>", "text/html");
	}

	private int getScrollTop(){
		Object result = webPage.executeScript(webPage.getMainFrame(), "window.scrollY");
		return result instanceof Integer ? (Integer)result : 0;
	}
}
