$(function () {
    var option = {
        url: '../sys/client/list',
        pagination: true,	//显示分页条
        sidePagination: 'server',//服务器端分页
        showRefresh: true,  //显示刷新按钮
        search: true,
        toolbar: '#toolbar',
        striped: true,     //设置为true会有隔行变色效果
        formatLoadingMessage: function () {
            return '数据加载中，请稍候...';
        },
        formatNoMatches: function () {
            return '暂无数据，请调整筛选条件';
        },
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
            {title: '公司名称', field: 'corpname'},
            {title: '公司地址', field: 'address'},
            {title: '联系人', field: 'linkman'},
            {title: '手机号', field: 'mobile'},
            {title: 'email', field: 'email'},
            {title: '客户经理', field: 'customermanager'}
        ]
    };
    $('#table').bootstrapTable(option);
});
var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        client: {},
        formErrors: {},
        saving: false
    },
    methods: {
        clearValidation: function () {
            vm.formErrors = {};
        },
        validateForm: function () {
            var errors = {};
            var mobileReg = /^[0-9+\-]{6,20}$/;
            var emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

            if (!vm.client.corpname || $.trim(vm.client.corpname) === '') {
                errors.corpname = '请输入公司名称';
            }

            if (!vm.client.linkman || $.trim(vm.client.linkman) === '') {
                errors.linkman = '请输入联系人';
            }

            if (!vm.client.mobile || $.trim(vm.client.mobile) === '') {
                errors.mobile = '请输入手机号';
            } else if (!mobileReg.test($.trim(vm.client.mobile))) {
                errors.mobile = '手机号格式不正确';
            }

            if (vm.client.email && !emailReg.test($.trim(vm.client.email))) {
                errors.email = '邮箱格式不正确';
            }

            vm.formErrors = errors;
            return Object.keys(errors).length === 0;
        },
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
                    url: "/sys/client/del",
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
            vm.client = {};
            vm.saving = false;
            vm.clearValidation();
        },
        update: function (event) {
            var id = 'id';
            var id = getSelectedRow()[id];
            if (id == null) {
                return;
            }

            $.get("../sys/client/info/" + id, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.client = (r && r.data) ? r.data.client : {};
                vm.saving = false;
                vm.clearValidation();
            });
        },
        saveOrUpdate: function (event) {
            if (vm.saving) {
                return;
            }

            if (!vm.validateForm()) {
                return;
            }

            var url = vm.client.id == null ? "../sys/client/save" : "../sys/client/update";
            vm.saving = true;
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.client),
                success: function (r) {
                    if (r.code === 0) {
                        layer.alert(r.msg, function (index) {
                            layer.close(index);
                            vm.reload();
                        });
                    } else {
                        layer.alert(r.msg);
                    }
                },
                complete: function () {
                    vm.saving = false;
                }
            });
        },
        reload: function (event) {
            vm.saving = false;
            vm.clearValidation();
            vm.showList = true;
            $("#table").bootstrapTable('refresh');
        }
    }
});

