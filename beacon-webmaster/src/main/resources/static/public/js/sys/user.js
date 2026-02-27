$(function () {

    $("#table").bootstrapTable({
        url: "/sys/user/list",
        pagination: true,
        sidePagination: "server",
        showRefresh: true,  //显示刷新按钮
        search: true,
        toolbar: '#toolbar',
        formatLoadingMessage: function () {
            return '数据加载中，请稍候...';
        },
        formatNoMatches: function () {
            return '暂无数据，请调整筛选条件';
        },
        columns: [
            {checkbox: true},
            {field: 'id', title: '编号', sortable: true},
            {field: 'usercode', title: '用户名'},
            {
                field: 'password', title: '密码', formatter: function (v, r, index) {
                    return "******";
                }
            },
            {field: 'email', title: '邮箱'},
            {field: 'realName', title: '真实姓名'},
            {
                field: 'type', title: '类型', formatter: function (v, r, i) {
                    if (v == 1) {
                        return "管理员";
                    } else {
                        return "普通客户";
                    }
                }
            },
            {
                field: 'status', title: '状态', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "无效";
                    } else {
                        return "有效";
                    }
                }
            },
            {field: 'clientid', title: '客户id'}
        ]
    });


});

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: '',
        sites: [],
        user: {},
        formErrors: {},
        saving: false
    },
    methods: {
        clearValidation: function () {
            vm.formErrors = {};
        },
        validateForm: function () {
            var errors = {};
            var emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

            if (!vm.user.usercode || $.trim(vm.user.usercode) === '') {
                errors.usercode = '请输入用户名';
            }

            if (vm.user.id == null) {
                if (!vm.user.password || $.trim(vm.user.password) === '') {
                    errors.password = '请输入密码';
                }
            }

            if (vm.user.email && !emailReg.test($.trim(vm.user.email))) {
                errors.email = '邮箱格式不正确';
            }

            if (!vm.user.realName || $.trim(vm.user.realName) === '') {
                errors.realName = '请输入真实姓名';
            }

            if (vm.user.clientid === null || vm.user.clientid === undefined || vm.user.clientid === '') {
                errors.clientid = '请选择所属客户';
            }

            vm.formErrors = errors;
            return Object.keys(errors).length === 0;
        },
        del: function () {
            //getSelectedRows();：common.js中定义   功能获得用户选择的记录  返回的是数组
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
                    ids[i] = row[id];//得到选择的这一行的id
                });

                //ids  = [1,2,3];//json数组
                $.ajax({
                    type: "POST",
                    url: "/sys/user/del",
                    data: JSON.stringify(ids),//把json数组转json字符串
                    success: function (r) {
                        if (r.code === 0) {//成功

                            layer.alert('删除成功');
                            //刷新
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
            vm.user = {parentName: null, parentId: 0, type: 1, orderNum: 0};
            vm.saving = false;
            vm.clearValidation();
            $.get("../sys/clientbusiness/all", function (r) {
                vm.sites = r.sites;
            });
        },
        update: function (event) {
            var id = 'id';
            var userId = getSelectedRow()[id];
            if (userId == null) {
                return;
            }
            $.get("../sys/user/info/" + userId, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.user = r.user;
                vm.saving = false;
                vm.clearValidation();
            });
            $.get("../sys/clientbusiness/all", function (r) {
                vm.sites = r.sites;
            });
        },
        saveOrUpdate: function (event) {
            //有菜单编号时是修改，没有：新增
            if (vm.saving) {
                return;
            }

            if (!vm.validateForm()) {
                return;
            }

            var url = vm.user.id == null ? "../sys/user/save" : "../sys/user/update";
            vm.saving = true;
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.user),//json字符串
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
