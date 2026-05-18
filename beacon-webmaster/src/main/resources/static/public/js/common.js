//ajax全局配置
$.ajaxSetup({
    dataType: "json",
    contentType: "application/json",
    cache: false
});

//选择多条记录
function getSelectedRows() {
    //返回所有选择的行，当没有选择的记录时，返回一个空数组
    var rows = $("#table").bootstrapTable('getSelections');
    if (rows.length == 0) {
        layer.alert('请选择一条记录');
        return;
    }
    return rows;
}

//选择一条记录
function getSelectedRow() {
    var emptyRow = {};
    //返回所有选择的行，当没有选择的记录时，返回一个空数组
    var rows = $("#table").bootstrapTable('getSelections');
    if (rows.length == 0) {
        layer.alert('请选择一条记录');
        return emptyRow;
    }

    if (rows.length > 1) {
        layer.alert("只能选择一条记录");
        return emptyRow;
    }

    return rows[0];
}

function hasPermission(permission) {
    var p = null;
    if (window.parent && $.isArray(window.parent.permissions)) {
        p = window.parent.permissions;
    } else if ($.isArray(window.permissions)) {
        p = window.permissions;
    }
    if (!p) {
        return true;
    }
    return p.indexOf(permission) > -1;
}

// bootstrap-table 全局空状态增强
$(function () {
    if ($.fn.bootstrapTable) {
        var _defaults = $.fn.bootstrapTable.defaults;
        var _origNoMatches = _defaults.formatNoMatches;
        _defaults.formatNoMatches = function () {
            return '<div class="ops-bt-empty">'
                + '<img class="ops-bt-empty-img" src="../public/images/empty-table.svg" alt="暂无数据">'
                + '<p class="ops-bt-empty-text">暂无数据</p>'
                + '<p class="ops-bt-empty-hint">当前列表为空，请新增记录或调整筛选条件</p>'
                + '</div>';
        };
    }
});

function renderEmptyState(opts) {
    opts = opts || {};
    var img = opts.img || '../public/images/empty-table.svg';
    var title = opts.title || '暂无数据';
    var desc = opts.desc || '当前暂无相关记录';
    var btnText = opts.btnText || '';
    var btnClick = opts.btnClick || '';
    var html = '<div class="ops-empty-state">'
        + '<img class="ops-empty-state-img" src="' + img + '" alt="' + title + '">'
        + '<p class="ops-empty-state-title">' + title + '</p>'
        + '<p class="ops-empty-state-desc">' + desc + '</p>';
    if (btnText && btnClick) {
        html += '<button type="button" class="btn btn-primary" onclick="' + btnClick + '">'
            + '<i class="fa fa-plus"></i>&nbsp;&nbsp;' + btnText + '</button>';
    }
    html += '</div>';
    return html;
}

function doTask(id, msg, url) {
    var rows = getSelectedRows();
    if (rows == null) {
        return;
    }
    //提示确认框
    layer.confirm('您确定要' + msg + '所选数据吗？', {
        btn: ['确定', '取消'] //可以无限个按钮
    }, function (index, layero) {
        var ids = new Array();
        //遍历所有选择的行数据，取每条数据对应的ID
        $.each(rows, function (i, row) {
            ids[i] = row[id];
        });

        $.ajax({
            type: "POST",
            url: url,
            data: JSON.stringify(ids),
            success: function (r) {
                if (r.code === 0) {
                    layer.alert(msg + '成功');
                    $('#table').bootstrapTable('refresh');
                } else {
                    layer.alert(r.msg);
                }
            },
            error: function () {
                layer.alert('服务器没有返回数据，可能服务器忙，请重试');
            }
        });
    });
}
