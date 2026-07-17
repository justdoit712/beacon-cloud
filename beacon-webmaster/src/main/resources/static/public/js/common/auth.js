;(function (window, $) {
    if (!$ || window.__beaconAuthAjaxSetup) {
        return;
    }
    window.__beaconAuthAjaxSetup = true;

    function normalizeToken(token) {
        if (!token) {
            return "";
        }
        token = $.trim(token);
        token = token.replace(/^[\s:\uFF1A]+/, "");
        if (token.indexOf("Bearer ") === 0) {
            token = token.substring(7);
        }
        return token;
    }

    $.ajaxSetup({
        beforeSend: function (xhr, settings) {
            var token = normalizeToken(localStorage.getItem("Auth-Token"));
            if (token && !/sys\/login/.test(settings.url || "")) {
                xhr.setRequestHeader("Authorization", "Bearer " + token);
            }
        },
        complete: function (xhr) {
            if (xhr.status == 401) {
                localStorage.removeItem("Auth-Token");
                top.location.href = 'login.html';
            }
        }
    });
})(window, window.jQuery);
