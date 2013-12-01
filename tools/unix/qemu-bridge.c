#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <regex.h>
#include <string.h>
#include <sys/stat.h>

#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <pwd.h>
#include <grp.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <linux/if_tun.h>

/* TUNSETGROUP appeared in 2.6.23 */
#ifndef TUNSETGROUP
#define TUNSETGROUP   _IOW('T', 206, int)
#endif

// mktap, deltap, ifup, ifdown, addtobr, delfrombr

printUsage() {
  printf("%s\n", "Usage: qemu-bridge ifup <interface> | ifdown <interface> | ifaddress <interface> <address> | mktap <tapname> <user> | deltap <tapname> | addtobr <br> <tap> | delfrombr <br> <tap>");
}

_system(const char *template, ...) {
  char buffer[256];
  va_list args;
  va_start(args, template);
  vsprintf(buffer, template, args);
  system(buffer);
  va_end(args);
}

_psystem(char *command, char *output) {
  FILE *fp;
  int status;
  char path[1035];
  
  fp = popen(command, "r");
  /* Read the output a line at a time - output it. */
  while (fgets(path, sizeof(path)-1, fp) != NULL) {
    sprintf(output, "%s", path);
  }

  pclose(fp);
}


ifup(char *interface) {
  _system("ip link set %s up promisc on", interface);
}

ifdown(char *interface) {
  _system("ip link set %s down", interface);
}

ifaddress(char *interface, char *address) {
  ifconfig(interface, address);
}

ifconfig(char *arg1, char *arg2) {
  _system("ifconfig %s %s", arg1, arg2);
}

int tunctl(char opt, char *tun, char *optuser) {
  struct ifreq ifr;
  struct passwd *pw;
  struct group *gr;
  uid_t owner = -1;
  gid_t group = -1;
  int tap_fd, delete = 0, brief = 0;
  char *file = "/dev/net/tun", *name = "tunctl", *end;

  if (opt == 'd') {
    delete = 1;
  }

  if (NULL != optuser) {
    pw = getpwnam(optuser);
    if(pw != NULL){
      owner = pw->pw_uid;
    } else {
      owner = strtol(optarg, &end, 0);
      if(*end != '\0'){
        fprintf(stderr, "'%s' is neither a username nor a numeric uid.\n", optarg);
      }
    }
  }

  if((tap_fd = open(file, O_RDWR)) < 0){
    fprintf(stderr, "Failed to open '%s' : ", file);
    perror("");
    return(1);
  }

  memset(&ifr, 0, sizeof(ifr));

  ifr.ifr_flags = IFF_TAP | IFF_NO_PI;
  strncpy(ifr.ifr_name, tun, sizeof(ifr.ifr_name) - 1);
  if(ioctl(tap_fd, TUNSETIFF, (void *) &ifr) < 0){
    perror("TUNSETIFF");
    return(1);
  }

  if(delete){
    if(ioctl(tap_fd, TUNSETPERSIST, 0) < 0){
      perror("disabling TUNSETPERSIST");
      return(1);
    }
    printf("Set '%s' nonpersistent\n", ifr.ifr_name);
  }
  else {
    if(owner == (uid_t)-1 && group == (gid_t)-1) {
      owner = geteuid();
    }

    if(owner != (uid_t)-1) {
      if(ioctl(tap_fd, TUNSETOWNER, owner) < 0){
        perror("TUNSETOWNER");
        return(1);
      }
    }
    if(group != (gid_t)-1) {
      if(ioctl(tap_fd, TUNSETGROUP, group) < 0){
        perror("TUNSETGROUP");
        return(1);
      }
    }

    if(ioctl(tap_fd, TUNSETPERSIST, 1) < 0){
      perror("enabling TUNSETPERSIST");
      return(1);
    }

    if(brief)
      printf("%s\n", ifr.ifr_name);
    else {
      printf("Set '%s' persistent and owned by", ifr.ifr_name);
      if(owner != (uid_t)-1)
          printf(" uid %d", owner);
      if(group != (gid_t)-1)
          printf(" gid %d", group);
      printf("\n");
    }
  }
  return(0);
}

mktap(char *tap, char *user) {
  tunctl('t', tap, user);
}

deltap(char *tap) {
  tunctl('d', tap, NULL);
}

addtobr(char *br, char *tap) {
  _system("brctl addif %s %s", br, tap);
}

delfrombr(char *br, char *tap) {
  _system("brctl delif %s %s", br, tap);
}

main(int argc, char *argv[]) {
  if (argc < 2) {
    printUsage();
    return 1;
  }

  const char *method = argv[1];

  if (strcmp(method, "ifup") == 0) {
    if (argc < 3) {
      printUsage();
      return 1;  
    }
    ifup(argv[2]);
  } else if (strcmp(method, "ifdown") == 0) {
    if (argc < 3) {
      printUsage();
      return 1;  
    }
    ifdown(argv[2]);
  } else if (strcmp(method, "ifaddress") == 0) {
    if (argc < 4) {
      printUsage();
      return 1;  
    }
    ifaddress(argv[2], argv[3]);
  } else if (strcmp(method, "mktap") == 0) {
    if (argc < 4) {
      printUsage();
      return 1;
    }
    mktap(argv[2], argv[3]);    
  } else if (strcmp(method, "deltap") == 0) {
    if (argc < 3) {
      printUsage();
      return 1;
    }
    deltap(argv[2]);
  } else if (strcmp(method, "addtobr") == 0) {
    if (argc < 4) {
      printUsage();
      return 1;
    }
    addtobr(argv[2], argv[3]);
  } else if (strcmp(method, "delfrombr") == 0) {
    if (argc < 4) {
      printUsage();
      return 1;
    }
    delfrombr(argv[2], argv[3]);
  } else {
    printUsage();
  }

  return 0;
}