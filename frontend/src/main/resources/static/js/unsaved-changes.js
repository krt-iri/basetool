document.addEventListener("DOMContentLoaded", function () {
    if (window.__unsavedChangesInitialized) return;
    window.__unsavedChangesInitialized = true;

    let isDirty = false;
    let targetUrl = null;

    const modal = document.getElementById("unsaved-changes-modal");
    const leaveBtn = document.getElementById("unsaved-leave-btn");
    const stayBtn = document.getElementById("unsaved-stay-btn");

    // Track input changes
    document.addEventListener("input", function (e) {
        let form = e.target.closest("form");
        if (form && !form.classList.contains("no-track")) {
            isDirty = true;
        }
    });

    document.addEventListener("change", function (e) {
        let form = e.target.closest("form");
        if (form && !form.classList.contains("no-track")) {
            isDirty = true;
        }
    });

    // Expose reset function globally
    window.resetUnsavedChanges = function() {
        isDirty = false;
    };

    // Clear dirty flag on form submit
    document.addEventListener("submit", function () {
        isDirty = false;
    });

    // Intercept internal link clicks
    document.addEventListener("click", function (event) {
        let a = event.target.closest("a");
        
        if (!a || !a.href) return;
        
        // Exclude specific links that don't trigger normal navigation
        let href = a.getAttribute("href");
        if (!href || href === "#" || href.startsWith("#") || a.target === "_blank" || a.href.startsWith("javascript:")) {
            return;
        }

        // Only intercept if we have unsaved changes
        if (isDirty) {
            // Is it a download link or something we should ignore? We assume standard navigation.
            event.preventDefault();
            targetUrl = a.href;
            
            if (modal) {
                modal.style.display = "flex";
            }
        }
    });

    if (stayBtn) {
        stayBtn.addEventListener("click", function () {
            if (modal) modal.style.display = "none";
            targetUrl = null;
        });
    }

    if (leaveBtn) {
        leaveBtn.addEventListener("click", function () {
            isDirty = false;
            window.removeEventListener("beforeunload", beforeUnloadHandler);
            if (targetUrl) {
                window.location.href = targetUrl;
            }
        });
    }

    if (modal) {
        window.addEventListener("click", function (event) {
            if (event.target === modal) {
                modal.style.display = "none";
                targetUrl = null;
            }
        });
    }

    const beforeUnloadHandler = function (event) {
        if (isDirty) {
            event.preventDefault();
            event.returnValue = ''; // Trigger native browser dialog
        }
    };
    
    // Externe Navigation abfangen (Reload, Tab close, etc.)
    window.addEventListener("beforeunload", beforeUnloadHandler);
});
