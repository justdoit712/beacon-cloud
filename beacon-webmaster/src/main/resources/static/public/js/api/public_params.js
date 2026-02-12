$(function () {
    var option = {
        url: '../sys/publicparams/list',
        pagination: true,	//显示分页条
        sidePagination: 'server',//服务器端分页
        showRefresh: true,  //显示刷新按钮
        search: true,
        toolbar: '#toolbar',
        striped: true,     //设置为true会有隔行变色效果
        //idField: 'menuId',
        columns: [
            {
                field: 'id',
                title: '序号',
                width: 40,
                formatter: function (value, row, index) {
                    var pageSize = $('#table').bootstrapTable('getOptions').pageSize;
                    var pageNumber = $('#table').bootstrapTable('getOptions').pageNumber;
                    return pageSize * (pageNumber - 1) + index + 1;
                }
            },
            {checkbox: true},
            {title: 'id', field: 'id', sortable: true},
            {title: '参数名', field: 'paramName'},
            {title: '参数类型', field: 'paramType'},
            {
                title: '创建日期', field: 'createDate', formatter: function (value, row, index) {
                    if (!value) {
                        return null;
                    }
                    return formatDate(value);
                }
            },
            {title: '描述', field: 'descripton'},
            {
                title: '是否必填', field: 'isMust', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "可选";
                    } else if (v == 1) {
                        return "必填";
                    }
                }
            },
            {
                title: '启用状态', field: 'enableState', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "停用";
                    } else if (v == 1) {
                        return "启用";
                    }
                }
            }
        ]
    };
    $('#table').bootstrapTable(option);
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        param: {}
    },
    methods: {
        del: function () {
            var rows = getSelectedRows();
            if (rows == null) {
                return;
            }
            var id = 'id';
            //提示确认框
            layer.confirm('您确定要删除所选数据吗？', {
                btn: ['确定', '取消'] //可以无限个按钮
            }, function (index, layero) {
                var ids = new Array();
                //遍历所有选择的行数据，取每条数据对应的ID
                $.each(rows, function (i, row) {
                    ids[i] = row[id];
                });

                $.ajax({
                    type: "POST",
                    url: "/sys/publicparams/del",
                    data: JSON.stringify(ids),
                    success: function (r) {
                        if (r.code === 0) {
                            layer.alert('删除成功');
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
        },
        add: function () {
            vm.showList = false;
            vm.title = "新增";
            vm.param = {};
        },
        update: function (event) {
            var id = 'id';
            var id = getSelectedRow()[id];
            if (id == null) {
                return;
            }

            $.get("../sys/publicparams/info/" + id, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.param = r.param;
            });
        },
        saveOrUpdate: function (event) {
            var url = vm.param.id == null ? "../sys/publicparams/save" : "../sys/publicparams/update";
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.param),
                success: function (r) {
                    if (r.code === 0) {
                        layer.alert(r.msg, function (index) {
                            layer.close(index);
                            vm.reload();
                        });
                    } else {
                        layer.alert(r.msg);
                    }
                }
            });
        },
        reload: function (event) {
            vm.showList = true;
            $("#table").bootstrapTable('refresh');
        }
    }
});
function formatDate(time) {
    var date = new Date(time);
    return date.getFullYear() + "-" + date.getMonth() + "-" + date.getDate() + " " + date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds()
}