package com.stockroom.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.dom.client.StyleInjector;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class StockroomEntryPoint implements EntryPoint {
    private final RootPanel root = RootPanel.get();
    private FlowPanel appShell;
    private FlowPanel sidebar;
    private FlowPanel mainContent;
    private final List<FlowPanel> navGroups = new ArrayList<FlowPanel>();
    private final List<Product> products = new ArrayList<Product>();
    private JSONObject user;
    private JSONObject store;
    private String currentView = "Overview";
    private String activeTab = "All";
    private int threshold = 15;
    private Label notice;
    private TextBox search;
    private String searchQuery = "";
    private TextBox ocrExpiryInput;
    private Button ocrScanButton;
    private Label ocrScanStatus;
    private Timer ocrScanTimer;
    private int ocrRequestId;

    @Override public void onModuleLoad() {
        installTheme();
        root.clear();
        root.setStyleName("sr-root");
        showLoading("Opening your workspace…");
        request("GET", "/api/auth/me", null, new ApiCallback() {
            @Override public void success(JSONValue data) { setAccount(data.isObject()); loadDashboard(); }
            @Override public void failure(String message) { showAuth(false, ""); }
        });
    }

    private void installTheme() {
        StyleInjector.inject("""
            :root{--ink:#17251e;--muted:#738078;--green:#236d4d;--green-dark:#174b36;--mint:#eaf4ed;--canvas:#f5f7f3;--line:#e7ebe5;--amber:#a65a18;--red:#b4493f}
            .sr-auth-card{max-width:calc(100vw - 56px)}
            html,body,#app,.gwt-RootPanel,.sr-root{min-height:100%;margin:0;width:100%;background:var(--canvas);font-family:'DM Sans','Segoe UI',Arial,sans-serif;color:var(--ink)}
            *{box-sizing:border-box}.sr-root{min-height:100vh}.sr-auth-page{min-height:100vh;display:grid;place-items:center;padding:28px;background:radial-gradient(ellipse at 20% 8%,#e7f2e9,transparent 35%),radial-gradient(ellipse at 90% 90%,#e9efe7,transparent 34%),var(--canvas)}
            .sr-auth-card{width:min(100%,460px);background:#fff;border:1px solid #e8ece7;border-radius:24px;padding:36px;box-shadow:0 24px 65px rgba(25,54,39,.10)}.sr-auth-logo{font:800 24px Manrope,sans-serif;color:var(--green-dark);margin:0 0 30px}.sr-auth-title{font:800 30px Manrope,sans-serif;letter-spacing:-.8px;margin:0 0 8px}.sr-auth-subtitle{color:var(--muted);line-height:1.55;margin:0 0 22px}.sr-auth-note{border-top:1px solid var(--line);padding-top:18px;margin-top:20px;color:var(--muted);font-size:13px;line-height:1.5}
            .sr-app{min-height:100vh;display:grid;grid-template-columns:258px minmax(0,1fr);max-width:1680px;margin:0 auto;background:var(--canvas)}.sr-sidebar{position:sticky;top:0;height:100vh;display:flex;flex-direction:column;padding:26px 18px 18px;background:#fff;border-right:1px solid var(--line)}.sr-brand{font:800 23px Manrope,sans-serif;color:var(--green-dark);padding:0 14px 25px}.sr-store-card{display:flex;align-items:center;gap:12px;text-align:left;padding:13px;border:1px solid var(--line);border-radius:15px;background:#fafbf9;margin-bottom:27px}.sr-store-icon{width:40px;height:40px;display:grid;place-items:center;background:#eaf1e7;border-radius:12px;font-size:20px}.sr-store-name{font-weight:700;font-size:13px;line-height:1.35}.sr-store-location{font-size:12px;color:var(--muted);margin-top:3px}.sr-nav-label{font-size:10px;letter-spacing:1.5px;font-weight:700;color:#97a29a;padding:0 14px 8px}.sr-nav-group{display:flex;flex-direction:column;gap:4px;margin-bottom:24px}.sr-nav-button{width:100%;display:flex;align-items:center;gap:12px;text-align:left!important;border:0!important;background:transparent!important;color:#58665d!important;padding:11px 13px!important;border-radius:11px!important;font-size:14px!important;font-weight:600!important;box-shadow:none!important}.sr-nav-button:hover{background:#f4f7f3!important;color:var(--green-dark)!important}.sr-nav-active{background:#eaf4ed!important;color:var(--green-dark)!important}.sr-nav-icon{width:19px;color:#849087;font-size:16px;text-align:center}.sr-support-card{margin-top:auto;padding:16px;border:1px solid var(--line);border-radius:16px;background:#f8faf7}.sr-support-title{font-weight:700;font-size:14px;margin-bottom:5px}.sr-support-copy{color:var(--muted);font-size:12px;line-height:1.5;margin-bottom:12px}.sr-support-button{padding:0!important;border:0!important;background:transparent!important;color:var(--green)!important;font-weight:700!important;box-shadow:none!important}.sr-account{border-top:1px solid var(--line);margin-top:16px;padding:15px 6px 0;display:flex;align-items:center;gap:10px}.sr-avatar{width:38px;height:38px;border-radius:50%;background:#e5efe7;display:grid;place-items:center;color:var(--green-dark);font-weight:700}.sr-account-name{font-size:13px;font-weight:700}.sr-account-role{font-size:11px;color:var(--muted);margin-top:2px}
            .sr-main{min-width:0;padding:28px clamp(20px,4vw,56px) 56px}.sr-topbar{display:flex;justify-content:flex-end;align-items:center;gap:10px;min-height:42px;margin-bottom:32px}.sr-topbar-store{margin-right:auto;color:var(--muted);font-size:13px}.sr-topbar-button{border:1px solid var(--line)!important;background:#fff!important;border-radius:10px!important;padding:9px 13px!important;color:#435148!important;font-size:13px!important;font-weight:600!important;box-shadow:none!important}.sr-page-heading{margin:0 0 23px}.sr-page-title{font:800 27px Manrope,sans-serif;letter-spacing:-.6px;margin:0 0 5px}.sr-page-subtitle{font-size:13px;color:var(--muted);margin:0}
            .sr-notice{padding:11px 14px;border:1px solid #d5e8d9;border-radius:11px;background:#edf7ef;color:#245a3e;font-size:13px;margin:0 0 16px}.sr-notice:empty{display:none}.sr-section-title{font:700 16px Manrope,sans-serif;margin:24px 0 12px}.sr-section-subtitle{color:var(--muted);font-size:13px;margin:-6px 0 14px}.sr-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:25px}.sr-stat{background:#fff;border:1px solid var(--line);border-radius:16px;padding:18px 19px;min-width:0}.sr-stat-label{color:var(--muted);font-size:12px;font-weight:600;margin-bottom:12px}.sr-stat-value{font:800 25px Manrope,sans-serif;letter-spacing:-.6px}.sr-stat-foot{font-size:11px;color:var(--muted);margin-top:6px}.sr-card{background:#fff;border:1px solid var(--line);border-radius:16px;padding:20px;margin-bottom:17px;min-width:0}.sr-card-heading{font:700 15px Manrope,sans-serif;margin:0 0 4px}.sr-card-description{font-size:12px;color:var(--muted);margin:0 0 16px}
            .sr-toolbar{display:flex;gap:9px;align-items:center;flex-wrap:wrap;margin-bottom:14px}.sr-toolbar .gwt-TextBox{flex:1;min-width:220px}.sr-tabs{display:flex;gap:6px;margin:2px 0 14px}.sr-tab{background:transparent!important;color:#68756c!important;border:1px solid transparent!important;padding:8px 12px!important;box-shadow:none!important;font-size:12px!important}.sr-tab-active{background:var(--mint)!important;color:var(--green-dark)!important;border-color:#d9e9dc!important}.gwt-Button{font-family:inherit;cursor:pointer;border:1px solid #dfe6df;border-radius:10px;background:#fff;color:#35443a;padding:9px 13px;font-size:13px;font-weight:600;transition:background .15s ease,border-color .15s ease,transform .15s ease}.gwt-Button:hover{background:#f5f8f5;border-color:#cdd8ce}.gwt-Button:active{transform:translateY(1px)}.sr-primary-button{background:var(--green)!important;border:1px solid var(--green)!important;border-radius:10px!important;color:#fff!important;padding:10px 14px!important;font:600 13px 'DM Sans','Segoe UI',sans-serif!important;cursor:pointer}.sr-primary-button:hover{background:var(--green-dark)!important;border-color:var(--green-dark)!important}.gwt-TextBox,.gwt-PasswordTextBox,.gwt-ListBox,.gwt-TextArea{width:100%;min-height:42px;padding:10px 12px;border:1px solid #dfe5df;border-radius:10px;background:#fff;color:var(--ink);font:14px 'DM Sans','Segoe UI',sans-serif;outline:none}.sr-form input,.sr-form select{box-sizing:border-box!important;min-height:42px!important;padding:10px 12px!important;border:1px solid #dfe5df!important;border-radius:10px!important;background:#fff!important;color:var(--ink)!important;font:14px 'DM Sans','Segoe UI',sans-serif!important}.gwt-TextBox:focus,.gwt-PasswordTextBox:focus,.gwt-ListBox:focus,.gwt-TextArea:focus{border-color:#5c9b76;box-shadow:0 0 0 3px rgba(52,128,81,.10)}.sr-form-field{width:100%;margin:0 0 10px}.sr-form{width:100%}.sr-form .gwt-TextBox,.sr-form .gwt-PasswordTextBox{width:100%}.sr-form .gwt-Button{margin-right:7px}.sr-table-wrap{width:100%;overflow-x:auto}.sr-table{width:100%;border-collapse:collapse;background:#fff;font-size:12px}.sr-table td{padding:12px 11px;border-bottom:1px solid #edf0ec;white-space:nowrap;color:#536057}.sr-table tr:first-child td{background:#f8faf7;color:#7b877e;font-weight:700;font-size:10px;text-transform:uppercase;letter-spacing:.65px}.sr-table tr:last-child td{border-bottom:0}.sr-table .gwt-Button{font-size:11px;padding:6px 9px}.sr-empty{padding:28px;text-align:center;color:var(--muted);font-size:13px}.sr-loading{min-height:100vh;display:grid;place-items:center;color:var(--muted);font-size:14px}.sr-dialog{background:#fff!important;opacity:1!important;border:0!important;border-radius:18px!important;box-shadow:0 25px 80px rgba(20,40,28,.22)!important;padding:0!important;max-width:min(92vw,560px);max-height:calc(100vh - 28px);overflow-y:auto;z-index:1001!important}.sr-dialog .Caption{background:#fff!important;border:0!important;border-bottom:1px solid var(--line)!important;padding:18px 22px!important;font:700 16px Manrope,sans-serif!important;color:var(--ink)!important}.sr-dialog .dialogContent{background:#fff!important;padding:19px 22px 22px!important}.sr-dialog .sr-form{min-width:min(70vw,400px)}.sr-dialog .gwt-VerticalPanel{width:100%}.sr-scan-status{max-width:380px;color:var(--muted);font-size:12px;line-height:1.5}.sr-error{color:var(--red);font-size:13px;margin:5px 0 10px}.sr-help-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.sr-help-item{background:#fff;border:1px solid var(--line);border-radius:15px;padding:18px}.sr-help-item strong{display:block;margin-bottom:7px}.sr-help-item span{display:block;color:var(--muted);font-size:13px;line-height:1.6}
            .sr-content{width:100%;min-width:0}.sr-danger-button{color:#a8463d!important;border-color:#f0dad6!important}.sr-status-good{color:#286b48!important;font-weight:700}.sr-status-warn{color:#a65a18!important;font-weight:700}.sr-status-low{color:#b4493f!important;font-weight:700}.sr-store-card,.sr-account,.sr-nav-button{cursor:pointer}.sr-store-card:hover{border-color:#cddfcf;background:#f6faf6}.sr-account:hover{background:#f8faf7;border-radius:10px}.sr-content .gwt-Label{line-height:1.5}.sr-form .gwt-Button{margin-top:4px}
            @media(max-width:960px){.sr-app{grid-template-columns:220px minmax(0,1fr)}.sr-sidebar{padding:22px 13px}.sr-stats{grid-template-columns:repeat(2,minmax(0,1fr))}.sr-main{padding:24px}}
            @media(max-width:640px){.sr-app{display:block}.sr-sidebar{position:relative;height:auto;padding:15px 15px 12px;border-right:0;border-bottom:1px solid var(--line)}.sr-brand{padding:0 7px 12px;font-size:21px}.sr-store-card{margin-bottom:12px;padding:9px}.sr-nav-label{display:none}.sr-nav-group{flex-direction:row;overflow-x:auto;margin:0 -4px 2px;padding:3px 4px 7px}.sr-nav-button{width:auto!important;white-space:nowrap;padding:9px 11px!important;font-size:12px!important}.sr-support-card,.sr-account{display:none}.sr-main{padding:20px 15px 36px}.sr-topbar{margin-bottom:22px;justify-content:flex-start;flex-wrap:wrap}.sr-topbar-store{width:100%;margin:0 0 4px}.sr-page-title{font-size:23px}.sr-stats{gap:9px}.sr-stat{padding:14px}.sr-stat-value{font-size:21px}.sr-card{padding:15px}.sr-help-grid{grid-template-columns:1fr}.sr-auth-card{padding:27px 22px;border-radius:19px}}
            """);
    }

    private void showLoading(String message) {
        FlowPanel loading = new FlowPanel(); loading.setStyleName("sr-loading"); loading.add(new Label(message)); root.add(loading);
    }

    private void setAccount(JSONObject account) {
        user = account.get("user").isObject();
        store = account.get("store").isObject();
    }

    private void showAuth(boolean signup, String error) {
        root.clear();
        FlowPanel page = new FlowPanel(); page.setStyleName("sr-auth-page");
        VerticalPanel card = new VerticalPanel(); card.setSpacing(10); card.setWidth("460px"); card.setStyleName("sr-auth-card sr-form");
        Label logo=styledLabel("⌂  stockroom", "#216746", 22, true);logo.setStyleName("sr-auth-logo");card.add(logo);
        Label title=styledLabel(signup ? "Set up your shop" : "Welcome back", "#17251d", 27, true);title.setStyleName("sr-auth-title");card.add(title);
        Label subtitle=new Label(signup ? "A calmer way to keep your shelves, suppliers and expiry dates under control." : "Sign in to see your store, stock and expiry alerts.");subtitle.setStyleName("sr-auth-subtitle");card.add(subtitle);
        final Label authError=new Label(error);authError.setStyleName("sr-error");card.add(authError);
        if (signup) {
            TextBox name = field("Your name", card);
            TextBox email = field("Email address", card);
            PasswordTextBox password = new PasswordTextBox(); password.getElement().setAttribute("placeholder", "Password (8 characters minimum)"); password.setWidth("100%");password.setStyleName("sr-form-field");styleInput(password); card.add(password);
            TextBox shop = field("Shop name", card);
            TextBox location = field("Location (optional)", card);
            Button create = new Button("Create my shop account"); create.setStyleName("sr-primary-button"); create.addClickHandler(e -> {
                JSONObject body = new JSONObject(); put(body,"name",name.getValue()); put(body,"email",email.getValue()); put(body,"password",password.getValue()); put(body,"storeName",shop.getValue()); put(body,"storeLocation",location.getValue());
                request("POST","/api/auth/signup",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());loadDashboard();}public void failure(String m){authError.setText(m);}});
            }); card.add(create);
        } else {
            TextBox email = field("Email address", card);
            PasswordTextBox password = new PasswordTextBox(); password.getElement().setAttribute("placeholder", "Password"); password.setWidth("100%");password.setStyleName("sr-form-field");styleInput(password); card.add(password);
            Button login = new Button("Log in to your store"); login.setStyleName("sr-primary-button"); login.addClickHandler(e -> {
                JSONObject body=new JSONObject();put(body,"email",email.getValue());put(body,"password",password.getValue());
                request("POST","/api/auth/login",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());loadDashboard();}public void failure(String m){authError.setText(m);}});
            }); card.add(login);
        }
        Button switchMode = new Button(signup ? "Already have an account? Log in" : "Create a shopkeeper account");
        switchMode.setStyleName("sr-topbar-button");switchMode.addClickHandler(e -> showAuth(!signup,"")); card.add(switchMode);
        Label note=new Label("Your workspace keeps inventory, suppliers and settings together for your shop.");note.setStyleName("sr-auth-note");card.add(note);
        page.add(card); root.add(page);
    }

    private TextBox field(String placeholder, VerticalPanel panel) { TextBox box=new TextBox();box.setWidth("100%");box.setStyleName("sr-form-field");styleInput(box);box.getElement().setAttribute("placeholder",placeholder);box.getElement().setAttribute("aria-label",placeholder);panel.add(box);return box; }

    private void styleInput(Widget input){input.getElement().getStyle().setProperty("boxSizing","border-box");input.getElement().getStyle().setProperty("minHeight","42px");input.getElement().getStyle().setProperty("padding","10px 12px");input.getElement().getStyle().setProperty("border","1px solid #dfe5df");input.getElement().getStyle().setProperty("borderRadius","10px");input.getElement().getStyle().setProperty("backgroundColor","#ffffff");input.getElement().getStyle().setProperty("fontFamily","Segoe UI, Arial, sans-serif");input.getElement().getStyle().setProperty("fontSize","14px");}

    private Label styledLabel(String text,String color,int size,boolean bold){Label label=new Label(text);label.getElement().getStyle().setColor(color);label.getElement().getStyle().setFontSize(size,Unit.PX);if(bold)label.getElement().getStyle().setProperty("fontWeight","700");return label;}

    private void loadDashboard() {
        root.clear();
        appShell=new FlowPanel();appShell.setStyleName("sr-app");
        sidebar=new FlowPanel();sidebar.setStyleName("sr-sidebar");
        navGroups.clear();notice=new Label("");notice.setStyleName("sr-notice");
        Label brand=new Label("⌂  stockroom");brand.setStyleName("sr-brand");sidebar.add(brand);
        FlowPanel storeButton=new FlowPanel();storeButton.setStyleName("sr-store-card");storeButton.getElement().setAttribute("role","button");storeButton.getElement().setAttribute("tabindex","0");
        FlowPanel storeIcon=new FlowPanel();storeIcon.setStyleName("sr-store-icon");storeIcon.add(new Label("🌿"));storeButton.add(storeIcon);
        FlowPanel storeCopy=new FlowPanel();Label storeName=new Label(text(store,"name","Your shop"));storeName.setStyleName("sr-store-name");storeCopy.add(storeName);Label location=new Label(text(store,"location","Main store"));location.setStyleName("sr-store-location");storeCopy.add(location);storeButton.add(storeCopy);storeButton.add(new Label("⌄"));storeButton.addDomHandler(e->showStoreDialog(),ClickEvent.getType());sidebar.add(storeButton);
        addNavGroup("WORKSPACE",new String[][]{{"▦","Overview"},{"◇","Inventory"},{"◷","Expiry tracker"},{"⌂","Suppliers"},{"▥","Sales & reports"}});
        addNavGroup("PREFERENCES",new String[][]{{"⚙","Settings"}});
        FlowPanel support=new FlowPanel();support.setStyleName("sr-support-card");Label supportTitle=new Label("Need a hand?");supportTitle.setStyleName("sr-support-title");support.add(supportTitle);Label supportCopy=new Label("Find quick answers and tips for keeping your shop up to date.");supportCopy.setStyleName("sr-support-copy");support.add(supportCopy);Button supportButton=new Button("Open help & guides  ↗");supportButton.setStyleName("sr-support-button");supportButton.addClickHandler(e->{currentView="Help & guides";renderContent();});support.add(supportButton);sidebar.add(support);
        FlowPanel account=new FlowPanel();account.setStyleName("sr-account");account.getElement().setAttribute("role","button");account.getElement().setAttribute("tabindex","0");Label avatar=new Label(initials(text(user,"name","S")));avatar.setStyleName("sr-avatar");account.add(avatar);FlowPanel accountCopy=new FlowPanel();Label accountName=new Label(text(user,"name","Shopkeeper"));accountName.setStyleName("sr-account-name");accountCopy.add(accountName);Label accountRole=new Label(text(user,"role","Store owner"));accountRole.setStyleName("sr-account-role");accountCopy.add(accountRole);account.add(accountCopy);account.addDomHandler(e->showProfileDialog(),ClickEvent.getType());sidebar.add(account);
        mainContent=new FlowPanel();mainContent.setStyleName("sr-main");appShell.add(sidebar);appShell.add(mainContent);root.add(appShell);
        notice=new Label("");notice.setStyleName("sr-notice");
        mainContent.add(new Label("Loading your inventory…"));
        request("GET","/api/settings",null,new ApiCallback(){public void success(JSONValue v){threshold=Math.max(1,Math.min(500,(int)number(v.isObject(),"lowStockThreshold")));loadProductsThen(StockroomEntryPoint.this::renderContent);}public void failure(String m){toast(m);loadProductsThen(StockroomEntryPoint.this::renderContent);}});
    }

    private void addNavGroup(String label,String[][] items){
        Label heading=new Label(label);heading.setStyleName("sr-nav-label");sidebar.add(heading);
        FlowPanel group=new FlowPanel();group.setStyleName("sr-nav-group");
        for(String[] item:items){String view=item[1];FlowPanel button=new FlowPanel();button.setStyleName("sr-nav-button"+(view.equals(currentView)?" sr-nav-active":""));button.getElement().setAttribute("data-view",view);button.getElement().setAttribute("role","button");button.getElement().setAttribute("tabindex","0");button.getElement().setAttribute("aria-label",view);Label icon=new Label(item[0]);icon.setStyleName("sr-nav-icon");button.add(icon);button.add(new Label(view));button.addDomHandler(e->{currentView=view;renderContent();},ClickEvent.getType());group.add(button);}
        navGroups.add(group);sidebar.add(group);
    }

    private void renderContent() {
        if (mainContent==null) return;
        refreshNavigation();
        mainContent.clear();
        FlowPanel topbar=new FlowPanel();topbar.setStyleName("sr-topbar");Label storeMeta=new Label(text(store,"name","Your shop")+"  ·  "+text(store,"location","Store workspace"));storeMeta.setStyleName("sr-topbar-store");topbar.add(storeMeta);
        Button storeDetails=new Button("Store details");storeDetails.setStyleName("sr-topbar-button");storeDetails.addClickHandler(e->showStoreDialog());topbar.add(storeDetails);
        Button profile=new Button("♙  "+first(text(user,"name","Shopkeeper")));profile.setStyleName("sr-topbar-button");profile.addClickHandler(e->showProfileDialog());topbar.add(profile);
        Button logout=new Button("Sign out");logout.setStyleName("sr-topbar-button");logout.addClickHandler(e->request("POST","/api/auth/logout","{}",new ApiCallback(){public void success(JSONValue v){user=null;store=null;showAuth(false,"You have signed out.");}public void failure(String m){toast("Could not sign out. Please try again.");}}));topbar.add(logout);
        mainContent.add(topbar);mainContent.add(notice);
        FlowPanel heading=new FlowPanel();heading.setStyleName("sr-page-heading");Label title=new Label(pageTitle());title.setStyleName("sr-page-title");heading.add(title);Label subtitle=new Label(pageSubtitle());subtitle.setStyleName("sr-page-subtitle");heading.add(subtitle);mainContent.add(heading);
        VerticalPanel app=new VerticalPanel();app.setWidth("100%");app.setStyleName("sr-content");mainContent.add(app);
        if(currentView.equals("Overview")){showOverview(app);return;}
        if(currentView.equals("Inventory")){showInventory(app);return;}
        if(currentView.equals("Expiry tracker")){showExpiry(app);return;}
        if(currentView.equals("Suppliers")){showSuppliers(app);return;}
        if(currentView.equals("Sales & reports")){showReports(app);return;}
        if(currentView.equals("Settings")){showSettings(app);return;}
        showHelp(app);
    }

    private void refreshNavigation(){for(FlowPanel group:navGroups)for(int i=0;i<group.getWidgetCount();i++){Widget widget=group.getWidget(i);if(widget instanceof FlowPanel button){String view=button.getElement().getAttribute("data-view");button.setStyleName("sr-nav-button"+(view.equals(currentView)?" sr-nav-active":""));}}}

    private String pageTitle(){return currentView.equals("Overview")?"Good day, "+first(text(user,"name","Shopkeeper")):currentView;}
    private String pageSubtitle(){if(currentView.equals("Overview"))return "Here’s what’s happening across your store today.";if(currentView.equals("Inventory"))return "Keep your products, quantities and expiry dates organized.";if(currentView.equals("Expiry tracker"))return "Catch products that need attention before they go out of date.";if(currentView.equals("Suppliers"))return "Keep your supplier contacts close at hand.";if(currentView.equals("Sales & reports"))return "Record sales and understand your stock performance.";if(currentView.equals("Settings"))return "Tune alerts to match the way your shop operates.";return "Quick answers to help you make the most of Stockroom.";}

    private void showOverview(VerticalPanel app) {
        int low=0,soon=0;double value=0;for(Product p:products){if(p.stock<threshold)low++;if(p.expiry.length()>0&&days(p.expiry)<=7)soon++;value+=p.price*p.stock;}
        FlowPanel stats=new FlowPanel();stats.setStyleName("sr-stats");stats.add(statCard("Products in stock",String.valueOf(products.size()),"Across all categories"));stats.add(statCard("Low stock",String.valueOf(low),"Below your alert threshold"));stats.add(statCard("Expiring soon",String.valueOf(soon),"Due within the next 7 days"));stats.add(statCard("Inventory value","₹"+Math.round(value),"Based on current quantities"));app.add(stats);
        VerticalPanel expiryCard=sectionCard("Expiry watch","Products that need attention soon");expiryCard.add(productTable(filter("Expiring"),false));app.add(expiryCard);
        VerticalPanel stockCard=sectionCard("Your inventory","A quick look at your products");stockCard.add(productTable(products.subList(0,Math.min(products.size(),6)),false));app.add(stockCard);
    }

    private FlowPanel statCard(String label,String value,String foot){FlowPanel card=new FlowPanel();card.setStyleName("sr-stat");Label l=new Label(label);l.setStyleName("sr-stat-label");card.add(l);Label v=new Label(value);v.setStyleName("sr-stat-value");card.add(v);Label f=new Label(foot);f.setStyleName("sr-stat-foot");card.add(f);return card;}
    private VerticalPanel sectionCard(String title,String description){VerticalPanel card=new VerticalPanel();card.setWidth("100%");card.setStyleName("sr-card");Label heading=new Label(title);heading.setStyleName("sr-card-heading");card.add(heading);Label sub=new Label(description);sub.setStyleName("sr-card-description");card.add(sub);return card;}

    private void showInventory(VerticalPanel app) {
        VerticalPanel card=sectionCard("Products","Search, review and adjust your current stock.");
        FlowPanel controls=new FlowPanel();controls.setStyleName("sr-toolbar");
        search=new TextBox();search.setValue(searchQuery);search.setWidth("100%");styleInput(search);search.getElement().setAttribute("placeholder","Search by product name or SKU");search.getElement().setAttribute("aria-label","Search inventory");search.addKeyUpHandler(e->{if(e.getNativeKeyCode()==13){searchQuery=search.getValue().trim();renderContent();}});Button find=new Button("Search");find.addClickHandler(e->{searchQuery=search.getValue().trim();renderContent();});controls.add(search);controls.add(find);
        Button add=new Button("＋  Add product");add.setStyleName("sr-primary-button");add.addClickHandler(e->showProductDialog());controls.add(add);card.add(controls);
        FlowPanel tabs=new FlowPanel();tabs.setStyleName("sr-tabs");for(final String t:new String[]{"All","Low stock","Expiring"}){Button b=new Button(t);b.setStyleName("sr-tab"+(activeTab.equals(t)?" sr-tab-active":""));b.addClickHandler(e->{activeTab=t;renderContent();});tabs.add(b);}card.add(tabs);
        card.add(productTable(filter(activeTab),true));app.add(card);
    }

    private void showExpiry(VerticalPanel app){VerticalPanel card=sectionCard("Expiry watch","Expiring and past-date items are shown first so you can act quickly.");card.add(productTable(filter("Expiring"),true));app.add(card);}

    private FlowPanel productTable(List<Product> list,boolean actions) {
        FlexTable table=new FlexTable();table.setCellPadding(5);table.setStyleName("sr-table");
        String[] headers={"Product","SKU","Category","Price","Stock","Expiry","Status"};for(int i=0;i<headers.length;i++)table.setText(0,i,headers[i]);
        for(int r=0;r<list.size();r++){final Product p=list.get(r);int row=r+1;table.setText(row,0,p.name);table.setText(row,1,p.sku);table.setText(row,2,p.category);table.setText(row,3,"₹"+p.price);table.setText(row,4,p.stock+" units");table.setText(row,5,p.expiry.isEmpty()?"—":p.expiry);table.setText(row,6,status(p));
            String state=status(p);table.getCellFormatter().addStyleName(row,6,state.equals("In stock")?"sr-status-good":state.equals("Expiring")?"sr-status-warn":"sr-status-low");
            if(actions){HorizontalPanel buttons=new HorizontalPanel();buttons.setSpacing(3);Button minus=new Button("−");minus.setTitle("Reduce stock");minus.addClickHandler(e->adjust(p,-1));Button plus=new Button("+");plus.setTitle("Increase stock");plus.addClickHandler(e->adjust(p,1));Button del=new Button("Remove");del.setStyleName("sr-danger-button");del.addClickHandler(e->deleteProduct(p));buttons.add(minus);buttons.add(plus);buttons.add(del);table.setWidget(row,7,buttons);}}
        if(list.isEmpty()){table.setText(1,0,"No products to display yet.");table.getCellFormatter().addStyleName(1,0,"sr-empty");}FlowPanel wrap=new FlowPanel();wrap.setStyleName("sr-table-wrap");wrap.add(table);return wrap;
    }

    private void showProductDialog() {
        final DialogBox dialog=new DialogBox();dialog.setText("Add a product");dialog.setStyleName("sr-dialog");VerticalPanel form=new VerticalPanel();form.setSpacing(7);form.setStyleName("sr-form");
        final TextBox name=field("Product name",form);final ListBox category=new ListBox();category.setStyleName("sr-form-field");category.setWidth("100%");styleInput(category);for(String c:new String[]{"Dairy","Produce","Bakery","Pantry","Other"})category.addItem(c);form.add(category);
        final TextBox price=field("Price (₹)",form),stock=field("Quantity in stock",form),expiry=field("Expiry date (YYYY-MM-DD)",form);
        VerticalPanel scanPanel=new VerticalPanel();scanPanel.setSpacing(5);scanPanel.add(new Label("Scan an expiry date from a clear, well-lit label photo"));FileUpload image=new FileUpload();image.setName("image");image.getElement().setAttribute("accept","image/*");scanPanel.add(image);Button scanButton=new Button("Scan label");Label scanStatus=new Label("Choose a photo, then scan the printed expiry date.");scanStatus.setStyleName("sr-scan-status");ocrExpiryInput=expiry;ocrScanButton=scanButton;ocrScanStatus=scanStatus;scanButton.addClickHandler(e->{if(image.getFilename().isEmpty()){toast("Choose a label photo first.");return;}final int requestId=++ocrRequestId;scanButton.setEnabled(false);scanStatus.setText("Reading the label…");if(ocrScanTimer!=null)ocrScanTimer.cancel();ocrScanTimer=new Timer(){public void run(){if(requestId==ocrRequestId){scanButton.setEnabled(true);scanStatus.setText("The scan timed out. Try a smaller photo or enter the date manually.");}}};ocrScanTimer.schedule(33000);sendExpiryImage(image.getElement(),requestId);});scanPanel.add(scanButton);scanPanel.add(scanStatus);form.add(scanPanel);
        HorizontalPanel buttons=new HorizontalPanel();buttons.setSpacing(6);Button save=new Button("Save product");save.setStyleName("sr-primary-button");save.addClickHandler(e->{try{if(name.getValue().trim().isEmpty()||Double.parseDouble(price.getValue())<0||Integer.parseInt(stock.getValue())<0)throw new IllegalArgumentException();JSONObject body=new JSONObject();put(body,"name",name.getValue().trim());put(body,"category",category.getSelectedValue());body.put("price",new JSONNumber(Double.parseDouble(price.getValue())));body.put("stock",new JSONNumber(Integer.parseInt(stock.getValue())));put(body,"icon","📦");if(!expiry.getValue().trim().isEmpty())put(body,"expiry",expiry.getValue().trim());request("POST","/api/products",body.toString(),new ApiCallback(){public void success(JSONValue v){dialog.hide();loadProductsThen(StockroomEntryPoint.this::renderContent);toast("Product added.");}public void failure(String m){toast(m);}});}catch(Exception ex){toast("Enter a product name, a valid price and a whole-number quantity.");}});
        Button cancel=new Button("Cancel");cancel.addClickHandler(e->dialog.hide());buttons.add(save);buttons.add(cancel);form.add(buttons);dialog.setWidget(form);dialog.center();
    }

    private void showSuppliers(VerticalPanel app) {
        VerticalPanel card=sectionCard("Add a supplier","Save contact details for your regular suppliers.");TextBox name=field("Supplier name",card),category=field("Category",card),phone=field("Phone (optional)",card),email=field("Email (optional)",card);
        Button add=new Button("Add supplier");add.setStyleName("sr-primary-button");add.addClickHandler(e->{if(name.getValue().trim().isEmpty()){toast("Enter the supplier name first.");return;}JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"category",category.getValue());put(body,"phone",phone.getValue());put(body,"email",email.getValue());request("POST","/api/suppliers",body.toString(),new ApiCallback(){public void success(JSONValue v){toast("Supplier added.");renderContent();}public void failure(String m){toast(m);}});});card.add(add);app.add(card);renderSuppliers(app);
    }
    private void renderSuppliers(final VerticalPanel app){request("GET","/api/suppliers",null,new ApiCallback(){public void success(JSONValue v){if(v.isArray()==null)return;JSONArray rows=v.isArray();VerticalPanel card=sectionCard("Your suppliers","Supplier details saved for this shop.");FlexTable t=new FlexTable();t.setCellPadding(5);t.setStyleName("sr-table");t.setText(0,0,"Supplier");t.setText(0,1,"Category");t.setText(0,2,"Contact");for(int i=0;i<rows.size();i++){JSONObject s=rows.get(i).isObject();int row=i+1;t.setText(row,0,text(s,"name",""));t.setText(row,1,text(s,"category",""));t.setText(row,2,text(s,"phone",text(s,"email","")));final String id=String.valueOf((long)number(s,"id"));Button remove=new Button("Remove");remove.setStyleName("sr-danger-button");remove.addClickHandler(e->request("DELETE","/api/suppliers/"+id,null,new ApiCallback(){public void success(JSONValue x){renderContent();toast("Supplier removed.");}public void failure(String m){toast(m);}}));t.setWidget(row,3,remove);}if(rows.size()==0){t.setText(1,0,"No suppliers yet. Add your first supplier above.");t.getCellFormatter().addStyleName(1,0,"sr-empty");}card.add(t);app.add(card);}public void failure(String m){toast(m);}});}

    private void showReports(final VerticalPanel app){
        int value=0,units=0,low=0,soon=0;for(Product p:products){value+=(int)(p.price*p.stock);units+=p.stock;if(p.stock<threshold)low++;if(days(p.expiry)<=7)soon++;}
        FlowPanel stats=new FlowPanel();stats.setStyleName("sr-stats");stats.add(statCard("Inventory value","₹"+value,"Across current products"));stats.add(statCard("Units on hand",String.valueOf(units),"Available to sell"));stats.add(statCard("Low stock",String.valueOf(low),"Products below threshold"));stats.add(statCard("Expiring soon",String.valueOf(soon),"Due within 7 days"));app.add(stats);
        VerticalPanel recordCard=sectionCard("Record a sale","Choose a product and enter the number of units sold.");
        final ListBox product=new ListBox();for(Product p:products)if(p.stock>0)product.addItem(p.name+" ("+p.stock+" available)",String.valueOf(p.id));
        product.setStyleName("sr-form-field");product.setWidth("100%");styleInput(product);final TextBox quantity=new TextBox();quantity.setValue("1");quantity.setStyleName("sr-form-field");styleInput(quantity);quantity.getElement().setAttribute("placeholder","Units sold");
        Button record=new Button("Record sale and update stock");record.setStyleName("sr-primary-button");record.addClickHandler(e->{try{int unitsSold=Integer.parseInt(quantity.getValue());if(product.getItemCount()==0){toast("Add stock before recording a sale.");return;}if(unitsSold<1)throw new NumberFormatException();JSONObject body=new JSONObject();body.put("productId",new JSONNumber(Double.parseDouble(product.getSelectedValue())));body.put("units",new JSONNumber(unitsSold));request("POST","/api/sales",body.toString(),new ApiCallback(){public void success(JSONValue v){toast("Sale recorded and stock updated.");loadProductsThen(StockroomEntryPoint.this::renderContent);}public void failure(String m){toast(m);}});}catch(Exception ex){toast("Choose a product and enter a whole-number quantity greater than zero.");}});
        FlowPanel controls=new FlowPanel();controls.setStyleName("sr-toolbar");controls.add(product);controls.add(quantity);controls.add(record);recordCard.add(controls);app.add(recordCard);
        request("GET","/api/sales",null,new ApiCallback(){public void success(JSONValue data){JSONObject summary=data.isObject();VerticalPanel card=sectionCard("Recent sales","Latest transactions recorded for this store.");Label totals=new Label(""+number(summary,"count")+" sales  ·  "+number(summary,"units")+" units  ·  ₹"+number(summary,"revenue")+" revenue");totals.setStyleName("sr-section-subtitle");card.add(totals);JSONArray rows=summary.get("recent").isArray();FlexTable table=new FlexTable();table.setCellPadding(5);table.setStyleName("sr-table");table.setText(0,0,"Product");table.setText(0,1,"Units");table.setText(0,2,"Unit price");table.setText(0,3,"Total");table.setText(0,4,"Date");for(int i=0;i<rows.size();i++){JSONObject s=rows.get(i).isObject();int row=i+1;table.setText(row,0,text(s,"product",""));table.setText(row,1,String.valueOf((int)number(s,"units")));table.setText(row,2,"₹"+number(s,"unitPrice"));table.setText(row,3,"₹"+number(s,"amount"));table.setText(row,4,text(s,"soldAt",""));}if(rows.size()==0){table.setText(1,0,"No sales recorded yet.");table.getCellFormatter().addStyleName(1,0,"sr-empty");}card.add(table);app.add(card);}public void failure(String m){toast(m);}});
    }

    private void showSettings(VerticalPanel app){VerticalPanel card=sectionCard("Stock alerts","Set the quantity that marks a product as low stock.");final TextBox input=field("Low stock threshold",card);input.setValue(String.valueOf(threshold));input.getElement().setAttribute("type","number");input.getElement().setAttribute("min","1");input.getElement().setAttribute("max","500");Button save=new Button("Save settings");save.setStyleName("sr-primary-button");save.addClickHandler(e->{try{int next=Integer.parseInt(input.getValue());if(next<1||next>500)throw new NumberFormatException();JSONObject body=new JSONObject();body.put("lowStockThreshold",new JSONNumber(next));request("PUT","/api/settings",body.toString(),new ApiCallback(){public void success(JSONValue v){threshold=(int)number(v.isObject(),"lowStockThreshold");toast("Settings saved.");renderContent();}public void failure(String m){toast(m);}});}catch(Exception ex){toast("Enter a whole number between 1 and 500.");}});card.add(save);app.add(card);}

    private void showHelp(VerticalPanel app){FlowPanel grid=new FlowPanel();grid.setStyleName("sr-help-grid");grid.add(helpItem("Add your first product","Open Inventory, choose Add product, then enter its name, price, quantity and expiry date.","Go to inventory","Inventory"));grid.add(helpItem("Scan an expiry label","In Add product, choose a clear package photo and select Scan label. Check the detected date before saving.","Open expiry tracker","Expiry tracker"));grid.add(helpItem("Keep stock accurate","Use the plus or minus controls on a product row after deliveries or sales. Record a sale in Sales & reports to update stock automatically.","Open sales & reports","Sales & reports"));grid.add(helpItem("Update your shop","Your name and email are under the owner profile. Store name and location are under Store details.","Open settings","Settings"));app.add(grid);}

    private FlowPanel helpItem(String title,String copy,String action,String view){FlowPanel card=new FlowPanel();card.setStyleName("sr-help-item");Label heading=new Label(title);heading.setStyleName("sr-card-heading");card.add(heading);Label body=new Label(copy);body.setStyleName("sr-card-description");card.add(body);Button go=new Button(action+"  →");go.setStyleName("sr-support-button");go.addClickHandler(e->{currentView=view;renderContent();});card.add(go);return card;}

    private void showProfileDialog(){final DialogBox d=new DialogBox();d.setText("Owner profile");d.setStyleName("sr-dialog");VerticalPanel p=new VerticalPanel();p.setSpacing(7);p.setStyleName("sr-form");final TextBox name=field("Full name",p);name.setValue(text(user,"name",""));final TextBox email=field("Email",p);email.setValue(text(user,"email",""));HorizontalPanel buttons=new HorizontalPanel();Button save=new Button("Save profile");save.setStyleName("sr-primary-button");save.addClickHandler(e->{JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"email",email.getValue());request("PUT","/api/auth/profile",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());d.hide();loadDashboard();toast("Profile saved.");}public void failure(String m){toast(m);}});});Button close=new Button("Close");close.addClickHandler(e->d.hide());buttons.add(save);buttons.add(close);p.add(buttons);d.setWidget(p);d.center();}
    private void showStoreDialog(){final DialogBox d=new DialogBox();d.setText("Store details");d.setStyleName("sr-dialog");VerticalPanel p=new VerticalPanel();p.setSpacing(7);p.setStyleName("sr-form");final TextBox name=field("Store name",p);name.setValue(text(store,"name",""));final TextBox location=field("Location",p);location.setValue(text(store,"location",""));Button save=new Button("Save store");save.setStyleName("sr-primary-button");save.addClickHandler(e->{JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"location",location.getValue());request("PUT","/api/store",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());d.hide();loadDashboard();toast("Store details saved.");}public void failure(String m){toast(m);}});});p.add(save);d.setWidget(p);d.center();}

    private List<Product> filter(String type){List<Product> out=new ArrayList<Product>();String q=currentView.equals("Inventory")?searchQuery.toLowerCase():"";for(Product p:products){boolean match=q.isEmpty()||p.name.toLowerCase().contains(q)||p.sku.toLowerCase().contains(q);if(!match)continue;if(type.equals("Low stock")&&p.stock>=threshold)continue;if(type.equals("Expiring")&&days(p.expiry)>7)continue;out.add(p);}return out;}
    private void adjust(final Product p,int delta){JSONObject body=productJson(p);body.put("stock",new JSONNumber(Math.max(0,p.stock+delta)));request("PUT","/api/products/"+p.id,body.toString(),new ApiCallback(){public void success(JSONValue v){loadProductsThen(StockroomEntryPoint.this::renderContent);}public void failure(String m){toast(m);}});}
    private void deleteProduct(final Product p){if(!Window.confirm("Remove "+p.name+" from inventory?"))return;request("DELETE","/api/products/"+p.id,null,new ApiCallback(){public void success(JSONValue v){loadProductsThen(StockroomEntryPoint.this::renderContent);toast("Product removed.");}public void failure(String m){toast(m);}});}
    private JSONObject productJson(Product p){JSONObject b=new JSONObject();put(b,"name",p.name);put(b,"category",p.category);b.put("price",new JSONNumber(p.price));b.put("stock",new JSONNumber(p.stock));put(b,"expiry",p.expiry);put(b,"icon",p.icon);return b;}
    private void loadProductsThen(final Runnable done){request("GET","/api/products",null,new ApiCallback(){public void success(JSONValue v){products.clear();JSONArray a=v.isArray();if(a!=null)for(int i=0;i<a.size();i++){JSONObject o=a.get(i).isObject();products.add(new Product(o));}done.run();}public void failure(String m){toast(m);done.run();}});}
    private void loadDashboardProducts(){loadProductsThen(this::renderContent);}
    private void loadProducts(){loadProductsThen(this::renderContent);}

    private void request(String method,String url,String body,final ApiCallback callback){try{RequestBuilder b=new RequestBuilder(requestMethod(method),url);b.setHeader("Accept","application/json");if(body!=null){b.setHeader("Content-Type","application/json");}b.sendRequest(body,new RequestCallback(){public void onResponseReceived(Request req,Response res){try{JSONValue v=JSONParser.parseStrict(res.getText());if(res.getStatusCode()>=200&&res.getStatusCode()<300)callback.success(v);else callback.failure(error(v,res.getStatusCode()));}catch(Exception e){if(res.getStatusCode()>=200&&res.getStatusCode()<300)callback.success(null);else callback.failure("Request failed ("+res.getStatusCode()+")");}}public void onError(Request req,Throwable e){callback.failure("Could not connect to Stockroom. Check your connection and try again.");}});}catch(RequestException e){callback.failure("Could not send the request.");}}
    private void toast(String message){if(notice!=null)notice.setText(message);else Window.alert(message);}
    private int days(String expiry){if(expiry==null||expiry.isEmpty())return 999;try{DateTimeFormat f=DateTimeFormat.getFormat("yyyy-MM-dd");Date a=f.parseStrict(expiry),b=f.parseStrict(f.format(new Date()));return (int)((a.getTime()-b.getTime())/86400000L);}catch(Exception e){return 999;}}
    private String status(Product p){if(p.stock==0)return "Out of stock";if(p.stock<threshold)return "Low stock";if(days(p.expiry)<=7)return "Expiring";return "In stock";}
    private String extractDate(String text){if(text==null)return "";for(int i=0;i+10<=text.length();i++){String x=text.substring(i,i+10);if(x.charAt(0)=='2'&&x.charAt(1)=='0'&&x.charAt(4)=='-'&&x.charAt(7)=='-'&&digits(x,0,4)&&digits(x,5,7)&&digits(x,8,10))return x;}return "";}
    private String extractOcrDate(String response){String json=cleanOcrResponse(response);try{JSONObject body=JSONParser.parseStrict(json).isObject();if(body!=null){String date=text(body,"date","");if(date.matches("20\\d{2}-\\d{2}-\\d{2}"))return date;}}catch(Exception ignored){}return extractDate(json);}
    private String extractOcrError(String response){String json=cleanOcrResponse(response);try{JSONObject body=JSONParser.parseStrict(json).isObject();if(body!=null)return text(body,"error","");}catch(Exception ignored){}return "The label scan could not be completed. Please enter the expiry date manually.";}
    private String cleanOcrResponse(String response){if(response==null)return "";String clean=response.trim();if(clean.startsWith("<")){clean=clean.replaceAll("(?is)<[^>]*>"," ").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&");}return clean;}
    private native void sendExpiryImage(Element input,int requestId) /*-{
        var self=this;
        var file=input.files&&input.files.length?input.files[0]:null;
        if(!file){self.@com.stockroom.client.StockroomEntryPoint::finishOcrError(Ljava/lang/String;I)("Choose a label photo first.",requestId);return;}
        var data=new FormData();data.append("image",file,file.name);
        var xhr=new XMLHttpRequest();xhr.open("POST","/api/ocr/expiry",true);xhr.timeout=30000;
        xhr.onload=function(){self.@com.stockroom.client.StockroomEntryPoint::finishOcrResponse(Ljava/lang/String;II)(xhr.responseText||"",xhr.status,requestId);};
        xhr.onerror=function(){self.@com.stockroom.client.StockroomEntryPoint::finishOcrError(Ljava/lang/String;I)("Could not reach the expiry scanner. Check your connection and try again.",requestId);};
        xhr.ontimeout=function(){self.@com.stockroom.client.StockroomEntryPoint::finishOcrError(Ljava/lang/String;I)("The scan took too long. Try a smaller photo or enter the date manually.",requestId);};
        xhr.send(data);
    }-*/;
    private void finishOcrResponse(String response,int statusCode,int requestId){if(requestId!=ocrRequestId)return;finishOcrRequest();String detected=extractOcrDate(response),error=extractOcrError(response);if(statusCode>=200&&statusCode<300&&!detected.isEmpty()){ocrExpiryInput.setValue(detected);ocrScanStatus.setText("Date detected. Review it before saving.");toast("Detected expiry date: "+detected+" — please review it.");return;}if(!error.isEmpty()&&!(statusCode>=200&&statusCode<300)){ocrScanStatus.setText(error);toast(error);return;}String readable="";try{JSONObject body=JSONParser.parseStrict(cleanOcrResponse(response)).isObject();if(body!=null)readable=text(body,"text","");}catch(Exception ignored){}ocrScanStatus.setText(readable.isEmpty()?"Could not identify an expiry date. Try a closer photo with EXP / Use by visible, or enter the date manually.":"Label read: "+readable+" — expiry date not recognized. Enter it manually.");}
    private void finishOcrError(String message,int requestId){if(requestId!=ocrRequestId)return;finishOcrRequest();ocrScanStatus.setText(message);toast(message);}
    private void finishOcrRequest(){if(ocrScanTimer!=null)ocrScanTimer.cancel();if(ocrScanButton!=null)ocrScanButton.setEnabled(true);}
    private boolean digits(String x,int from,int to){for(int i=from;i<to;i++)if(x.charAt(i)<'0'||x.charAt(i)>'9')return false;return true;}
    private RequestBuilder.Method requestMethod(String name){if(name.equals("POST"))return RequestBuilder.POST;if(name.equals("PUT"))return RequestBuilder.PUT;if(name.equals("DELETE"))return RequestBuilder.DELETE;return RequestBuilder.GET;}
    private static void put(JSONObject o,String key,String value){o.put(key,new JSONString(value==null?"":value));}
    private static String text(JSONObject o,String key,String fallback){if(o==null||o.get(key)==null||o.get(key).isString()==null)return fallback;return o.get(key).isString().stringValue();}
    private static double number(JSONObject o,String key){if(o==null||o.get(key)==null)return 0;try{if(o.get(key).isNumber()!=null)return o.get(key).isNumber().doubleValue();if(o.get(key).isString()!=null)return Double.parseDouble(o.get(key).isString().stringValue());}catch(Exception ignored){}return 0;}
    private static String first(String name){int i=name.indexOf(' ');return i<0?name:name.substring(0,i);}
    private static String initials(String name){String[] parts=name.trim().split("\\s+");if(parts.length==0||parts[0].isEmpty())return "S";return parts.length>1?parts[0].substring(0,1).toUpperCase(Locale.ROOT)+parts[1].substring(0,1).toUpperCase(Locale.ROOT):parts[0].substring(0,1).toUpperCase(Locale.ROOT);}
    private static String error(JSONValue v,int code){if(v!=null&&v.isObject()!=null){String e=text(v.isObject(),"error","");if(!e.isEmpty())return e;}return "Request failed ("+code+")";}

    private interface ApiCallback { void success(JSONValue data); void failure(String message); }
    private static final class Product {final long id;final String name,sku,category,expiry,icon;final int stock;final double price;Product(JSONObject o){id=(long)number(o,"id");name=text(o,"name","");sku=text(o,"sku","");category=text(o,"category","");expiry=text(o,"expiry","");icon=text(o,"icon","📦");stock=(int)number(o,"stock");price=number(o,"price");}}
}
