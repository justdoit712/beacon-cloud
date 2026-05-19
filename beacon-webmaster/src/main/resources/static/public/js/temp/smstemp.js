$(function () {
    var option = {
        url: '../sys/sms-template/list',
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
            {title: '签名ID', field: 'signId', sortable: true},
            {title: '模板内容', field: 'templateText'},
            {title: '模板类型', field: 'templateType', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "验证码类";
                    } else if (v == 1) {
                        return "通知类";
                    } else if (v == 2) {
                        return "营销类";
                    }
                }
            },
            {title: '审核状态', field: 'templateState', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "审核中";
                    } else if (v == 1) {
                        return "审核不通过";
                    } else if (v == 2) {
                        return "审核通过";
                    }
                }
            },
            {title: '使用场景', field: 'useId', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "网站";
                    } else if (v == 1) {
                        return "APP";
                    } else if (v == 2) {
                        return "微信";
                    }
                }
            },
            {title: '使用地址', field: 'useWeb'},
            {title: '更新时间', field: 'updated'}
        ]
    };
    $('#table').bootstrapTable(option);
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        smstemplate: {}
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
                    url: "/sys/sms-template/del",
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
            vm.smstemplate = {
                templateType: 0,
                templateState: 0,
                useId: 0
            };
        },
        update: function (event) {
            var id = 'id';
            var id = getSelectedRow()[id];
            if (id == null) {
                return;
            }

            $.get("../sys/sms-template/info/" + id, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.smstemplate = (r && r.data) ? r.data : {};
            });
        },
        saveOrUpdate: function (event) {
            var url = vm.smstemplate.id == null ? "../sys/sms-template/save" : "../sys/sms-template/update";
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.smstemplate),
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
